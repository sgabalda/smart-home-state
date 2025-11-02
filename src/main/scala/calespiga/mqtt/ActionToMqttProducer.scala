package calespiga.mqtt

import calespiga.model.Action
import cats.effect.IO
import cats.effect.Ref

trait ActionToMqttProducer {

  def actionToMqtt(action: Action.SendMqttStringMessage): IO[Unit]

}

object ActionToMqttProducer {

  final private case class Impl(
      producer: Producer,
      topicsBlacklist: Ref[IO, Set[String]]
  ) extends ActionToMqttProducer {
    override def actionToMqtt(action: Action.SendMqttStringMessage): IO[Unit] =
      topicsBlacklist.get.flatMap { blacklistedTopics =>
        if (blacklistedTopics.contains(action.topic)) {
          IO.unit
        } else {
          producer.publish(
            action.topic,
            action.message.getBytes("UTF-8").toVector
          )
        }
      }
  }

  def apply(
      producer: Producer,
      topicsBlacklist: Ref[IO, Set[String]]
  ): ActionToMqttProducer = Impl(producer, topicsBlacklist)

}
