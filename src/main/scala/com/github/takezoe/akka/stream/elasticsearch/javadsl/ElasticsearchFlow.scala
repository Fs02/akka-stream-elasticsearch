package com.github.takezoe.akka.stream.elasticsearch.javadsl

import java.util.{List => JavaList}

import akka.NotUsed
import akka.stream.scaladsl.Flow
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
      client: RestHighLevelClient,
      writer: T => String
  ): akka.stream.javadsl.Flow[IncomingMessage[T], JavaList[IncomingMessage[T]], NotUsed] =
    Flow
      .fromGraph(
        new ElasticsearchFlowStage[T, JavaList[IncomingMessage[T]]](indexName,
                                                                    typeName,
                                                                    client,
                                                                    settings.asScala,
                                                                    _.asJava,
                                                                    )(writer)
      )
      .mapAsync(1)(identity)
      .asJava

}
