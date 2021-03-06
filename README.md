akka-stream-elasticsearch [![Build Status](https://travis-ci.org/takezoe/akka-stream-elasticsearch.svg?branch=master)](https://travis-ci.org/takezoe/akka-stream-elasticsearch)
========

The Elasticsearch connector provides Akka Stream sources and sinks for Elasticsearch.

For more information about Elasticsearch please visit the [official documentation](https://www.elastic.co/guide/index.html).

This repository hasn't been maintained no longer because this connector is availavle as a part of Alpakka (https://github.com/akka/alpakka/pull/221).

## Artifacts

- sbt:

  ```scala
  libraryDependencies += "com.github.takezoe" %% "akka-stream-elasticsearch" % "1.1.0"
  ```

- Maven:

  ```xml
  <dependency>
    <groupId>com.github.takezoe</groupId>
    <artifactId>akka-stream-elasticsearch_2.12</artifactId>
    <version>1.1.0</version>
  </dependency>
  ```

- Gradle:

  ```gradle
  dependencies {
    compile group: "com.github.takezoe", name: "akka-stream-elasticsearch_2.12", version: "1.1.0"
  }
  ```

## Usage

Sources, Flows and Sinks provided by this connector need a prepared `RestClient` to access to Elasticsearch.

- Scala:

  ```scala
  implicit val client = RestClient.builder(new HttpHost("localhost", 9201)).build()
  ```

- Java:

  ```java
  client = RestClient.builder(new HttpHost("localhost", 9211)).build();
  ```

We will also need an `ActorSystem` and an `ActorMaterializer`.

- Scala:

  ```scala
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  ```

- Java:

  ```java
  system = ActorSystem.create();
  materializer = ActorMaterializer.create(system);
  ```

This is all preparation that we are going to need.

### JsObject message

Now we can stream messages which contains spray-json's `JsObject` (in Scala) or `java.util.Map<String, Object>` (in Java) 
from or to Elasticsearch where we have access to by providing the `RestClient` to the `ElasticsearchSource` or the `ElasticsearchSink`.

- Scala:

  ```scala
  val f1 = ElasticsearchSource(
    "source",
    "book",
    """{"match_all": {}}""",
    ElasticsearchSourceSettings(5)
  )
  .map { message: OutgoingMessage[JsObject] =>
    IncomingMessage(Some(message.id), message.source)
  }
  .runWith(
    ElasticsearchSink(
      "sink1",
      "book",
      ElasticsearchSinkSettings(5)
    )
  )
  ```

- Java:

  ```java
  CompletionStage<Done> f1 = ElasticsearchSource.create(
      "source",
      "book",
      "{\"match_all\": {}}",
      new ElasticsearchSourceSettings().withBufferSize(5),
      client)
      .map(m -> new IncomingMessage<>(new Some<String>(m.id()), m.source()))
      .runWith(
          ElasticsearchSink.create(
              "sink1",
              "book",
              new ElasticsearchSinkSettings().withBufferSize(5),
              client),
          materializer);
  ```

### Typed messages

Also, it's possible to stream messages which contains any classes. In Scala, spray-json is used for JSON conversion, 
so defining the mapped class and `JsonFormat` for it is necessary. In Java, Jackson is used, so just define the mapped class.

- Scala:

  ```scala
  case class Book(title: String)
  implicit val format = jsonFormat1(Book)
  ```

- Java:

  ```java
  public static class Book {
    public String title;
  }
  ```

Use `ElasticsearchSource.typed` and `ElasticsearchSink.typed` to create source and sink instead.

- Scala:

  ```scala
  val f1 = ElasticsearchSource
    .typed[Book](
      "source",
      "book",
      """{"match_all": {}}""",
      ElasticsearchSourceSettings(5)
    )
    .map { message: OutgoingMessage[Book] =>
      IncomingMessage(Some(message.id), message.source)
    }
    .runWith(
      ElasticsearchSink.typed[Book](
        "sink2",
        "book",
        ElasticsearchSinkSettings(5)
      )
    )
  ```

- Java:

  ```java
  CompletionStage<Done> f1 = ElasticsearchSource.typed(
      "source",
      "book",
      "{\"match_all\": {}}",
      new ElasticsearchSourceSettings().withBufferSize(5),
      client,
      Book.class)
      .map(m -> new IncomingMessage<>(new Some<String>(m.id()), m.source()))
      .runWith(
          ElasticsearchSink.typed(
              "sink2",
              "book",
              new ElasticsearchSinkSettings().withBufferSize(5),
              client),
          materializer);
  ```

### Configuration

We can configure the source by `ElasticsearchSourceSettings`.

Scala (source)

  ```scala
  final case class ElasticsearchSourceSettings(bufferSize: Int = 10)
  ```

| Parameter  | Default | Description                                                                                                              |
| ---------- | ------- | ------------------------------------------------------------------------------------------------------------------------ |
| bufferSize | 10      | `ElasticsearchSource` retrieves messages from Elasticsearch by scroll scan. This buffer size is used as the scroll size. | 

Also, we can configure the sink by `ElasticsearchSinkSettings`.

Scala (sink)

  ```scala
  final case class ElasticsearchSinkSettings(bufferSize: Int = 10,
                                             retryInterval: Int = 5000,
                                             maxRetry: Int = 100,
                                             retryPartialFailure: Boolean = true)
  ```

| Parameter           | Default | Description                                                                                            |
| ------------------- | ------- | ------------------------------------------------------------------------------------------------------ |
| bufferSize          | 10      | `ElasticsearchSink` puts messages by one bulk request per messages of this buffer size.                |
| retryInterval       | 5000    | When a request is failed, `ElasticsearchSink` retries that request after this interval (milliseconds). |
| maxRetry            | 100     | `ElasticsearchSink` give up and fails the stage if it gets this number of consective failures.         | 
| retryPartialFailure | true    | A bulk request might fails partially for some reason. If this parameter is true, then `ElasticsearchSink` retries to request these failed messages. Otherwise, failed messages are discarded (or pushed to downstream if you use `ElasticsearchFlow` instead of the sink). |

### Using Elasticsearch as a Flow

You can also build flow stages. The API is similar to creating Sinks.

- Scala (flow):

  ```scala
  val f1 = ElasticsearchSource
    .typed[Book](
      "source",
      "book",
      """{"match_all": {}}""",
      ElasticsearchSourceSettings(5)
    )
    .map { message: OutgoingMessage[Book] =>
      IncomingMessage(Some(message.id), message.source)
    }
    .via(
      ElasticsearchFlow.typed[Book](
        "sink3",
        "book",
        ElasticsearchSinkSettings(5)
      )
    )
    .runWith(Sink.seq)
  ```

- Java (flow):

  ```java
  CompletionStage<List<List<IncomingMessage<Book>>>> f1 = ElasticsearchSource.typed(
      "source",
      "book",
      "{\"match_all\": {}}",
      new ElasticsearchSourceSettings().withBufferSize(5),
      client,
      Book.class)
      .map(m -> new IncomingMessage<>(new Some<String>(m.id()), m.source()))
      .via(ElasticsearchFlow.typed(
          "sink3",
          "book",
          new ElasticsearchSinkSettings().withBufferSize(5),
          client))
      .runWith(Sink.seq(), materializer);
  ```

### Running the example code

The code in this guide is part of runnable tests of this project. You are welcome to edit the code and run it in sbt.

- Scala

  ```
  sbt
  > elasticsearch/testOnly *.ElasticsearchSpec
  ```

- Java

  ```
  sbt
  > elasticsearch/testOnly *.ElasticsearchTest
  ```
