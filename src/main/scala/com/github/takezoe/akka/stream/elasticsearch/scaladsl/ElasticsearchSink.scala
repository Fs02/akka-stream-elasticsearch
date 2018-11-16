package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.Done
import akka.stream.scaladsl.{Keep, Sink}
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

import scala.concurrent.Future

object ElasticsearchSink {

  /**
   * Scala API: creates a sink based on [[ElasticsearchFlowStage]]
   */
  def apply[T](indexName: String, typeName: String, settings: ElasticsearchSinkSettings)(
      implicit client: RestHighLevelClient,
      writer: T => String
  ): Sink[IncomingMessage[T], Future[Done]] =
    ElasticsearchFlow[T](indexName, typeName, settings).toMat(Sink.ignore)(Keep.right)

}
