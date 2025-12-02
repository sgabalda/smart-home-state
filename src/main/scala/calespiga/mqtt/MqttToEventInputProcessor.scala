package calespiga.mqtt

import calespiga.ErrorManager
import calespiga.model.Event.EventData
import cats.effect.IO
import fs2.Stream
import net.sigusr.mqtt.api.Message

trait MqttToEventInputProcessor {

  def inputEventsStream
      : Stream[IO, Either[ErrorManager.Error.MqttInputError, EventData]]

}

object MqttToEventInputProcessor {

  private final case class Impl(
      consumer: Consumer,
      conversions: TopicMessagesConverter
  ) extends MqttToEventInputProcessor {
    override def inputEventsStream
        : Stream[IO, Either[ErrorManager.Error.MqttInputError, EventData]] = {
      consumer.startConsumer().map { case Message(topic, payload) =>
        conversions.get(topic) match {
          case Some(conversion) =>
            conversion(payload).fold(
              error => Left(ErrorManager.Error.MqttInputError(error, topic)),
              eventData => Right(eventData)
            )
          case None =>
            Left(
              ErrorManager.Error.MqttInputError(
                new Exception(s"Topic $topic not found in conversions"),
                topic
              )
            )
        }
      }
    }
  }

  type TopicMessagesConverter =
    Map[String, Vector[Byte] => Either[Throwable, EventData]]

  def apply(
      consumer: Consumer,
      topicMessagesConverter: TopicMessagesConverter
  ): MqttToEventInputProcessor =
    Impl(consumer, topicMessagesConverter)

}
