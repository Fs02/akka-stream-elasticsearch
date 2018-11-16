package com.github.takezoe.akka.stream.elasticsearch

import akka.NotUsed
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.github.takezoe.akka.stream.elasticsearch.ElasticsearchFlowStage._
import com.github.takezoe.akka.stream.elasticsearch.scaladsl.ElasticsearchSinkSettings
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

final case class IncomingMessage[T](id: Option[String], source: T)

trait MessageWriter[T] {
  def convert(message: T): String
}

class ElasticsearchFlowStage[T, R](
    indexName: String,
    typeName: String,
    client: RestHighLevelClient,
    settings: ElasticsearchSinkSettings,
    pusher: Seq[IncomingMessage[T]] => R,
    writer: MessageWriter[T]
) extends GraphStage[FlowShape[IncomingMessage[T], Future[R]]] {

  private val in = Inlet[IncomingMessage[T]]("messages")
  private val out = Outlet[Future[R]]("failed")
  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogic(shape) with InHandler with OutHandler {

      private var state: State = Idle
      private val queue = new mutable.Queue[IncomingMessage[T]]()
      private val failureHandler = getAsyncCallback[(Seq[IncomingMessage[T]], Throwable)](handleFailure)
      private val responseHandler = getAsyncCallback[(Seq[IncomingMessage[T]], BulkResponse)](handleResponse)
      private var failedMessages: Seq[IncomingMessage[T]] = Nil
      private var retryCount: Int = 0

      override def preStart(): Unit =
        pull(in)

      private def tryPull(): Unit =
        if (queue.size < settings.bufferSize && !isClosed(in) && !hasBeenPulled(in)) {
          pull(in)
        }

      override def onTimer(timerKey: Any): Unit = {
        sendBulkUpdateRequest(failedMessages)
        failedMessages = Nil
      }

      private def handleFailure(args: (Seq[IncomingMessage[T]], Throwable)): Unit = {
        val (messages, exception) = args
        if (retryCount >= settings.maxRetry) {
          failStage(exception)
        } else {
          retryCount = retryCount + 1
          failedMessages = messages
          scheduleOnce(NotUsed, settings.retryInterval.millis)
        }
      }

      private def handleSuccess(): Unit =
        completeStage()

      private def handleResponse(args: (Seq[IncomingMessage[T]], BulkResponse)): Unit = {
        val (messages, response) = args

        val failed = response.getItems.zip(messages).flatMap {
          case (item, message) =>
            if (item.getFailureMessage != null) Some(message)
            else None
        }

        if (failed.nonEmpty && settings.retryPartialFailure) {
          // Retry partial failed messages
          retryCount = retryCount + 1
          failedMessages = failed
          scheduleOnce(NotUsed, settings.retryInterval.millis)

        } else {
          retryCount = 0

          // Fetch next messages from queue and send them
          val nextMessages = (1 to settings.bufferSize).flatMap { _ =>
            queue.dequeueFirst(_ => true)
          }

          if (nextMessages.isEmpty) {
            state match {
              case Finished => handleSuccess()
              case _ => state = Idle
            }
          } else {
            sendBulkUpdateRequest(nextMessages)
          }

          push(out, Future.successful(pusher(failed)))
        }
      }

      private def sendBulkUpdateRequest(messages: Seq[IncomingMessage[T]]): Unit = {
        val request = new BulkRequest()

        request.add(messages.map {
          case IncomingMessage(Some(id), source) =>
            new IndexRequest(indexName, typeName, id).source(writer.convert(source), XContentType.JSON)
          case IncomingMessage(None, source) =>
            new IndexRequest(indexName, typeName).source(writer.convert(source), XContentType.JSON)
        }: _*)

        client.bulkAsync(
          request,
          new ActionListener[BulkResponse] {
            override def onResponse(response: BulkResponse): Unit =
              responseHandler.invoke((messages, response))
            override def onFailure(exception: Exception): Unit =
              failureHandler.invoke((messages, exception))
          }
        )
      }

      setHandlers(in, out, this)

      override def onPull(): Unit = tryPull()

      override def onPush(): Unit = {
        val message = grab(in)
        queue.enqueue(message)

        state match {
          case Idle => {
            state = Sending
            val messages = (1 to settings.bufferSize).flatMap { _ =>
              queue.dequeueFirst(_ => true)
            }
            sendBulkUpdateRequest(messages)
          }
          case _ => ()
        }

        tryPull()
      }

      override def onUpstreamFailure(exception: Throwable): Unit =
        failStage(exception)

      override def onUpstreamFinish(): Unit =
        state match {
          case Idle => handleSuccess()
          case Sending => state = Finished
          case Finished => ()
        }
    }

}

object ElasticsearchFlowStage {

  private sealed trait State
  private case object Idle extends State
  private case object Sending extends State
  private case object Finished extends State

}
