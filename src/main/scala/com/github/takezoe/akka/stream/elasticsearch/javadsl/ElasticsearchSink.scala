package com.github.takezoe.akka.stream.elasticsearch.javadsl

import java.util.concurrent.CompletionStage

import akka.stream.javadsl._
import akka.{Done, NotUsed}
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

object ElasticsearchSink {

  /**
   * Java API: creates a sink based on [[ElasticsearchFlowStage]]
   */
  def create[T](indexName: String,
                typeName: String,
                settings: ElasticsearchSinkSettings,
                client: RestHighLevelClient,
                writer: T => String): akka.stream.javadsl.Sink[IncomingMessage[T], CompletionStage[Done]] =
    ElasticsearchFlow
      .create(indexName, typeName, settings, client, writer)
      .toMat(Sink.ignore, Keep.right[NotUsed, CompletionStage[Done]])

}
