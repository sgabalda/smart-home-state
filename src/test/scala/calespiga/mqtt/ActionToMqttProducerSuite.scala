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
      topicsBlacklist <- IO.ref[Set[String]](Set.empty)
      producer = ProducerStub((topic: String, payload: Vector[Byte]) =>
        storage.set(Some((topic, payload)))
      )
      actionToMqtt = ActionToMqttProducer(producer, topicsBlacklist)
      _ <- actionToMqtt.actionToMqtt(action)
      result <- storage.get
    } yield {
      assertEquals(result, expected)
    }
  }

  test(
    "Action to MqttProducer does not call producer if topic is blacklisted (exact match)"
  ) {
    val topic = "blacklisted/topic"
    val message = "message"
    val action = Action.SendMqttStringMessage(topic, message)
    for {
      storage <- IO.ref[Option[(String, Vector[Byte])]](None)
      topicsBlacklist <- IO.ref[Set[String]](Set(topic))
      producer = ProducerStub((topic: String, payload: Vector[Byte]) =>
        storage.set(Some((topic, payload)))
      )
      actionToMqtt = ActionToMqttProducer(producer, topicsBlacklist)
      _ <- actionToMqtt.actionToMqtt(action)
      result <- storage.get
    } yield {
      assertEquals(result, None)
    }
  }

}
