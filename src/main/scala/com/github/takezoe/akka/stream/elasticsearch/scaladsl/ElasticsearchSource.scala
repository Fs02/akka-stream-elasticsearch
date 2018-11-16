package com.github.takezoe.akka.stream.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient
import spray.json._

object ElasticsearchSource {

  /**
   * Scala API: creates a [[ElasticsearchSourceStage]]
   */
  def apply[T](indexName: String, typeName: String, query: String, settings: ElasticsearchSourceSettings)(
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
