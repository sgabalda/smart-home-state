package calespiga.mqtt

import calespiga.model.Action
import cats.effect.IO

object ActionToMqttProducerStub {

  def apply(
      actionToMqttStub: Action.SendMqttStringMessage => IO[Unit] =
        (_: Action.SendMqttStringMessage) => IO.unit
  ): ActionToMqttProducer = (action: Action.SendMqttStringMessage) =>
    actionToMqttStub(action)

}
