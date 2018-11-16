package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

object ElasticsearchSource {

  /**
   * Scala API: creates a [[ElasticsearchSourceStage]]
   */
  def apply[T](indexName: String, typeName: String, query: String, settings: ElasticsearchSourceSettings)(
      implicit client: RestHighLevelClient,
      reader: String => T
  ): Source[OutgoingMessage[T], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(indexName, typeName, query, client, settings)
    )

}
