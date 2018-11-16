package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient
import spray.json._

object ElasticsearchFlow {

  /**
   * Scala API: creates a [[ElasticsearchFlowStage]]
   */
  def apply[T](indexName: String, typeName: String, settings: ElasticsearchSinkSettings)(
      implicit client: RestHighLevelClient,
      writer: JsonWriter[T]
  ): Flow[IncomingMessage[T], Seq[IncomingMessage[T]], NotUsed] =
    Flow
      .fromGraph(
        new ElasticsearchFlowStage[T, Seq[IncomingMessage[T]]](indexName,
                                                               typeName,
                                                               client,
                                                               settings,
                                                               identity,
                                                               new SprayJsonWriter[T]()(writer))
      )
      .mapAsync(1)(identity)

  private class SprayJsonWriter[T](implicit writer: JsonWriter[T]) extends MessageWriter[T] {
    override def convert(message: T): String = message.toJson.toString()
  }

}
