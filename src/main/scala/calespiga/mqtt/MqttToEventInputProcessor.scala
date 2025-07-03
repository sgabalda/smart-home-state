package calespiga.mqtt

import calespiga.ErrorManager
import calespiga.model.Event
import cats.effect.IO
import fs2.Stream
import net.sigusr.mqtt.api.Message

trait MqttToEventInputProcessor {

  def inputEventsStream
      : Stream[IO, Either[ErrorManager.Error.MqttInputError, Event]]

}

object MqttToEventInputProcessor {

  private final case class Impl(
      consumer: Consumer,
      conversions: TopicMessagesConverter
  ) extends MqttToEventInputProcessor {
    override def inputEventsStream
        : Stream[IO, Either[ErrorManager.Error.MqttInputError, Event]] = {
      consumer.startConsumer().evalMap { case Message(topic, payload) =>
        conversions.get(topic) match {
          case Some(conversion) =>
            conversion(payload).fold(
              error =>
                IO.pure(Left(ErrorManager.Error.MqttInputError(error, topic))),
              eventData =>
                IO.realTimeInstant.map { timestamp =>
                  Right(Event(timestamp, eventData))
                }
            )
          case None =>
            IO.pure(
              Left(
                ErrorManager.Error.MqttInputError(
                  new Exception(s"Topic $topic not found in conversions"),
                  topic
                )
              )
            )
        }
      }
    }
  }

  type TopicMessagesConverter =
    Map[String, Vector[Byte] => Either[Throwable, Event.EventData]]

  def apply(
      consumer: Consumer,
      topicMessagesConverter: TopicMessagesConverter
  ): MqttToEventInputProcessor = {
    println(
      s"****** Creating MqttToEventInputProcessor with ${topicMessagesConverter.size} topic conversions: ${topicMessagesConverter.keys.mkString(", ")}"
    )
    Impl(consumer, topicMessagesConverter)
  }

}
