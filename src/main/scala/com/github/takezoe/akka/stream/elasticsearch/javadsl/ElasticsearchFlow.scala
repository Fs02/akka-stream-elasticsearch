package com.github.takezoe.akka.stream.elasticsearch.javadsl

import java.util.{List => JavaList, Map => JavaMap}

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.takezoe.akka.stream.elasticsearch._
import org.elasticsearch.client.RestHighLevelClient

import scala.collection.JavaConverters._

object ElasticsearchFlow {

  /**
   * Java API: creates a [[ElasticsearchFlowStage]]
   */
  def create[T](
      indexName: String,
      typeName: String,
      settings: ElasticsearchSinkSettings,
      client: RestHighLevelClient
  ): akka.stream.javadsl.Flow[IncomingMessage[T], JavaList[IncomingMessage[T]], NotUsed] =
    Flow
      .fromGraph(
        new ElasticsearchFlowStage[T, JavaList[IncomingMessage[T]]](indexName,
                                                                    typeName,
                                                                    client,
                                                                    settings.asScala,
                                                                    _.asJava,
                                                                    new JacksonWriter[T]())
      )
      .mapAsync(1)(identity)
      .asJava

  private class JacksonWriter[T] extends MessageWriter[T] {

    private val mapper = new ObjectMapper()

    override def convert(message: T): String =
      mapper.writeValueAsString(message)
  }

}
