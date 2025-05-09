package calespiga.mqtt

import calespiga.model.Action
import cats.effect.IO

trait ActionToMqttProducer {

  def actionToMqtt(action: Action.SendMqttStringMessage): IO[Unit]

}

object ActionToMqttProducer {

  final private case class Impl(producer: Producer)
      extends ActionToMqttProducer {
    override def actionToMqtt(action: Action.SendMqttStringMessage): IO[Unit] =
      producer.publish(action.topic, action.message.getBytes("UTF-8").toVector)
  }

  def apply(producer: Producer): ActionToMqttProducer = Impl(producer)

}
