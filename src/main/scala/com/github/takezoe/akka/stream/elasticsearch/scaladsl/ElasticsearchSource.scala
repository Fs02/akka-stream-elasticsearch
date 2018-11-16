package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient
import spray.json._

object ElasticsearchSource {

  /**
   * Scala API: creates a [[ElasticsearchSourceStage]] that consumes as JsObject
   */
  def apply(indexName: String, typeName: String, query: String, settings: ElasticsearchSourceSettings)(
      implicit client: RestHighLevelClient
  ): Source[OutgoingMessage[JsObject], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(
        indexName,
        typeName,
        query,
        client,
        settings,
        new SprayJsonReader[JsObject]()(DefaultJsonProtocol.RootJsObjectFormat)
      )
    )

  /**
   * Scala API: creates a [[ElasticsearchSourceStage]] that consumes as specific type
   */
  def typed[T](indexName: String, typeName: String, query: String, settings: ElasticsearchSourceSettings)(
      implicit client: RestHighLevelClient,
      reader: JsonReader[T]
  ): Source[OutgoingMessage[T], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(indexName, typeName, query, client, settings, new SprayJsonReader[T]()(reader))
    )

  private class SprayJsonReader[T](implicit reader: JsonReader[T]) extends MessageReader[T] {

    override def convert(json: String): T = json.parseJson.convertTo[T]

  }

}
