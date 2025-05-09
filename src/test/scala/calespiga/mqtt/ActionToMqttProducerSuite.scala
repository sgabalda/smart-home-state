package calespiga.mqtt

import calespiga.model.Action
import cats.effect.IO
import munit.CatsEffectSuite

class ActionToMqttProducerSuite extends CatsEffectSuite {

  test("Action to MqttProducer calls the producer with the right parameters") {
    val topic = "ToPiC"
    val message = "message"
    val action = Action.SendMqttStringMessage(topic, message)
    val expected = Some(topic, message.getBytes("UTF-8").toVector)
    for {
      storage <- IO.ref[Option[(String, Vector[Byte])]](None)
      producer = ProducerStub((topic: String, payload: Vector[Byte]) =>
        storage.set(Some((topic, payload)))
      )
      actionToMqtt = ActionToMqttProducer(producer)
      _ <- actionToMqtt.actionToMqtt(action)
      result <- storage.get
    } yield {
      assertEquals(result, expected)
    }
  }

}
