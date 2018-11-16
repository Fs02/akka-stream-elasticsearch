package com.github.takezoe.akka.stream.elasticsearch.javadsl

import akka.NotUsed
import akka.stream.javadsl.Source
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

object ElasticsearchSource {

  /**
   * Java API: creates a [[ElasticsearchSourceStage]]
   */
  def create[T](indexName: String,
                typeName: String,
                query: String,
                settings: ElasticsearchSourceSettings,
                client: RestHighLevelClient,
                writer: String => T): Source[OutgoingMessage[T], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(
        indexName,
        typeName,
        query,
        client,
        settings.asScala
      )(writer)
    )

}
