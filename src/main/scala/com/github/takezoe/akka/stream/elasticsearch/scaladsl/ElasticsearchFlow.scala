package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

object ElasticsearchFlow {

  /**
   * Scala API: creates a [[ElasticsearchFlowStage]]
   */
  def apply[T](indexName: String, typeName: String, settings: ElasticsearchSinkSettings)(
      implicit client: RestHighLevelClient,
      writer: T => String
  ): Flow[IncomingMessage[T], Seq[IncomingMessage[T]], NotUsed] =
    Flow
      .fromGraph(
        new ElasticsearchFlowStage[T, Seq[IncomingMessage[T]]](indexName, typeName, client, settings, identity)
      )
      .mapAsync(1)(identity)

}
