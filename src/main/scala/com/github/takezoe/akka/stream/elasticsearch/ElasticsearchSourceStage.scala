package com.github.takezoe.akka.stream.elasticsearch

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.github.takezoe.akka.stream.elasticsearch.scaladsl.ElasticsearchSourceSettings
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.{SearchRequest, SearchResponse, SearchScrollRequest}
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.search.builder.SearchSourceBuilder

final case class OutgoingMessage[T](id: String, source: T)

trait MessageReader[T] {
  def convert(json: String): T
}

final class ElasticsearchSourceStage[T](indexName: String,
                                        typeName: String,
                                        query: String,
                                        client: RestHighLevelClient,
                                        settings: ElasticsearchSourceSettings,
                                        reader: MessageReader[T])
    extends GraphStage[SourceShape[OutgoingMessage[T]]] {

  val out: Outlet[OutgoingMessage[T]] = Outlet("ElasticsearchSource.out")
  override val shape: SourceShape[OutgoingMessage[T]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new ElasticsearchSourceLogic[T](indexName, typeName, query, client, settings, out, shape, reader)

}

sealed class ElasticsearchSourceLogic[T](indexName: String,
                                         typeName: String,
                                         query: String,
                                         client: RestHighLevelClient,
                                         settings: ElasticsearchSourceSettings,
                                         out: Outlet[OutgoingMessage[T]],
                                         shape: SourceShape[OutgoingMessage[T]],
                                         reader: MessageReader[T])
    extends GraphStageLogic(shape)
    with ActionListener[SearchResponse]
    with OutHandler {

  private var scrollId: String = null
  private val responseHandler = getAsyncCallback[SearchResponse](handleResponse)
  private val failureHandler = getAsyncCallback[Throwable](handleFailure)

  def sendScrollScanRequest(): Unit =
    try {
      if (scrollId == null) {
        val searchSourceBuilder = new SearchSourceBuilder
        // searchSourceBuilder.query(matchQuery("title", "Elasticsearch")) TODO: query
        searchSourceBuilder.size(settings.bufferSize)

        val searchRequest = new SearchRequest(indexName)
        searchRequest.source(searchSourceBuilder)
        searchRequest.scroll(TimeValue.timeValueMinutes(5))

        client.searchAsync(searchRequest, this)
      } else {
        val scrollRequest = new SearchScrollRequest(scrollId)
        scrollRequest.scroll(TimeValue.timeValueMinutes(5))

        client.searchScrollAsync(scrollRequest, this)
      }
    } catch {
      case ex: Exception => handleFailure(ex)
    }

  override def onFailure(exception: Exception) = failureHandler.invoke(exception)
  override def onResponse(response: SearchResponse) = responseHandler.invoke(response)

  def handleFailure(ex: Throwable): Unit =
    failStage(ex)

  def handleResponse(res: SearchResponse): Unit =
    if (res.getFailedShards > 0)
      failStage(new IllegalStateException(res.getShardFailures.head.getCause))
    else if (res.getHits.getHits.isEmpty)
      completeStage()
    else {
      scrollId = res.getScrollId
      val message = res.getHits.getHits.map(hit => OutgoingMessage(hit.getId, reader.convert(hit.getSourceAsString)))
      emitMultiple(out, message.toIterator)
    }

  setHandler(out, this)

  override def onPull(): Unit = sendScrollScanRequest()

}
