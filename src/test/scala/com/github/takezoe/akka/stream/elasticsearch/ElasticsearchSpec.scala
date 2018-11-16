/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package com.github.takezoe.akka.stream.elasticsearch

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import com.github.takezoe.akka.stream.elasticsearch.scaladsl._
import org.apache.http.HttpHost
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHeader
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import spray.json.DefaultJsonProtocol._

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ElasticsearchSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  private val runner = new ElasticsearchClusterRunner()

  //#init-mat
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //#init-mat
  //#init-client
  implicit val lclient = RestClient.builder(new HttpHost("localhost", 9201)).build()
  implicit val client = new RestHighLevelClient(lclient)
  //#init-client

  //#define-class
  case class Book(title: String)
  implicit val format = jsonFormat1(Book)
  //#define-class

  override def beforeAll() = {
    runner.build(
      ElasticsearchClusterRunner
        .newConfigs()
        .baseHttpPort(9200)
        .baseTransportPort(9300)
        .numOfNode(1)
    )
    runner.ensureYellow()

    register("source", "Akka in Action")
    register("source", "Programming in Scala")
    register("source", "Learning Scala")
    register("source", "Scala for Spark in Production")
    register("source", "Scala Puzzlers")
    register("source", "Effective Akka")
    register("source", "Akka Concurrency")
    flush("source")
  }

  override def afterAll() = {
    runner.close()
    runner.clean()
    lclient.close()
    TestKit.shutdownActorSystem(system)
  }

  private def flush(indexName: String): Unit =
    lclient.performRequest("POST", s"$indexName/_flush")

  private def register(indexName: String, title: String): Unit =
    lclient.performRequest("POST",
                           s"$indexName/book",
                           Map[String, String]().asJava,
                           new StringEntity(s"""{"title": "$title"}"""),
                           new BasicHeader("Content-Type", "application/json"))

  "Elasticsearch connector" should {
    "consume and publish documents as specific type" in {
      // Copy source/book to sink2/book through typed stream
      //#run-typed
      val f1 = ElasticsearchSource[Book](
        "source",
        "book",
        """{"match_all": {}}""",
        ElasticsearchSourceSettings(5)
      ).map { message: OutgoingMessage[Book] =>
          IncomingMessage(Some(message.id), message.source)
        }
        .runWith(
          ElasticsearchSink[Book](
            "sink2",
            "book",
            ElasticsearchSinkSettings(5)
          )
        )
      //#run-typed

      Await.result(f1, Duration.Inf)

      flush("sink2")

      // Assert docs in sink2/book
      val f2 = ElasticsearchSource[Book](
        "sink2",
        "book",
        """{"match_all": {}}""",
        ElasticsearchSourceSettings()
      ).map { message =>
          message.source.title
        }
        .runWith(Sink.seq)

      val result = Await.result(f2, Duration.Inf)

      result.sorted shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }
  }

  "ElasticsearchFlow" should {
    "store documents and pass Responses" in {
      // Copy source/book to sink3/book through typed stream
      //#run-flow
      val f1 = ElasticsearchSource[Book](
        "source",
        "book",
        """{"match_all": {}}""",
        ElasticsearchSourceSettings(5)
      ).map { message: OutgoingMessage[Book] =>
          IncomingMessage(Some(message.id), message.source)
        }
        .via(
          ElasticsearchFlow[Book](
            "sink3",
            "book",
            ElasticsearchSinkSettings(5)
          )
        )
        .runWith(Sink.seq)
      //#run-flow

      val result1 = Await.result(f1, Duration.Inf)
      flush("sink3")

      // Assert no error
      assert(result1.forall(_.isEmpty))

      // Assert docs in sink3/book
      val f2 = ElasticsearchSource[Book](
        "sink3",
        "book",
        """{"match_all": {}}""",
        ElasticsearchSourceSettings()
      ).map { message =>
          message.source.title
        }
        .runWith(Sink.seq)

      val result2 = Await.result(f2, Duration.Inf)

      result2.sorted shouldEqual Seq(
        "Akka Concurrency",
        "Akka in Action",
        "Effective Akka",
        "Learning Scala",
        "Programming in Scala",
        "Scala Puzzlers",
        "Scala for Spark in Production"
      )
    }
  }

  "ElasticsearchFlow" should {
    "not post invalid encoded JSON" in {

      val books = Seq(
        "Akka in Action",
        "Akka \u00DF Concurrency"
      )

      val f1 = Source(books.zipWithIndex.toVector)
        .map {
          case (book: String, index: Int) =>
            IncomingMessage(Some(index.toString), Book(book))
        }
        .via(
          ElasticsearchFlow[Book](
            "sink4",
            "book",
            ElasticsearchSinkSettings(5)
          )
        )
        .runWith(Sink.seq)

      val result1 = Await.result(f1, Duration.Inf)
      flush("sink4")

      // Assert no error
      assert(result1.forall(_.isEmpty))

      // Assert docs in sink3/book
      val f2 = ElasticsearchSource[Book](
        "sink4",
        "book",
        """{"match_all": {}}""",
        ElasticsearchSourceSettings()
      ).map { message =>
          message.source.title
        }
        .runWith(Sink.seq)

      val result2 = Await.result(f2, Duration.Inf)

      result2.sorted shouldEqual Seq(
        "Akka in Action",
        "Akka \u00DF Concurrency"
      )
    }
  }

}
