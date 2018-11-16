package com.github.takezoe.akka.stream.elasticsearch.javadsl

import akka.NotUsed
import akka.stream.javadsl.Source
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

object ElasticsearchSource {

  /**
   * Java API: creates a [[ElasticsearchSourceStage]] that consumes as java.util.Map
   */
  def create(indexName: String,
             typeName: String,
             query: String,
             settings: ElasticsearchSourceSettings,
             client: RestHighLevelClient): Source[OutgoingMessage[java.util.Map[String, Object]], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(
        indexName,
        typeName,
        query,
        client,
        settings.asScala,
        new JacksonReader[java.util.Map[String, Object]](classOf[java.util.Map[String, Object]])
      )
    )

  /**
   * Java API: creates a [[ElasticsearchSourceStage]] that consumes as specific type
   */
  def typed[T](indexName: String,
               typeName: String,
               query: String,
               settings: ElasticsearchSourceSettings,
               client: RestHighLevelClient,
               clazz: Class[T]): Source[OutgoingMessage[T], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(
        indexName,
        typeName,
        query,
        client,
        settings.asScala,
        new JacksonReader[T](clazz)
      )
    )

  private class JacksonReader[T](clazz: Class[T]) extends MessageReader[T] {

    private val mapper = new ObjectMapper()

    override def convert(json: String): T = mapper.readValue(json, clazz)
  }

}
