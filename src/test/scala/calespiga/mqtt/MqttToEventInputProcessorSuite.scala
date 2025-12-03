package calespiga.mqtt

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.mqtt.MqttToEventInputProcessor.TopicMessagesConverter
import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import net.sigusr.mqtt.api.Message

class MqttToEventInputProcessorSuite extends CatsEffectSuite {

  private val emptyConverter: TopicMessagesConverter = Map.empty

  test("on inputEventsStream should start consumer") {
    for {
      called <- IO.ref(false)
      consumer = ConsumerStub(
        startConsumerStub =
          () => Stream.eval(called.set(true)).flatMap(_ => Stream.empty)
      )
      sut = MqttToEventInputProcessor(consumer, emptyConverter)
      _ <- sut.inputEventsStream.compile.drain
      calledValue <- called.get
    } yield {
      assertEquals(calledValue, true, "Consumer was not called")
    }
  }

  test("if the conversion returns an error, it should be propagated") {
    val error = new Exception("Conversion error")
    val topic = "TestTopic"
    val expectedError = ErrorManager.Error.MqttInputError(error, topic)
    val consumer = ConsumerStub(
      startConsumerStub = () => Stream(Message(topic, Vector.empty[Byte]))
    )
    val faultyConverter: TopicMessagesConverter =
      Map(topic -> (_ => Left(error)))

    val sut = MqttToEventInputProcessor(consumer, faultyConverter)
    for {
      last <- sut.inputEventsStream.compile.last
    } yield {
      assertEquals(
        last,
        Some(Left(expectedError)),
        "Conversion error was not propagated"
      )
    }
  }

  test(
    "if the Consumer returns a message for topic not in the converter, an error should be propagated"
  ) {

    val topic = "TestTopic"
    val consumer = ConsumerStub(
      startConsumerStub = () => Stream(Message(topic, Vector.empty[Byte]))
    )
    val sut = MqttToEventInputProcessor(consumer, emptyConverter)
    for {
      last <- sut.inputEventsStream.compile.last
    } yield {
      last match {
        case Some(Left(ErrorManager.Error.MqttInputError(e, topicError))) =>
          assert(
            e.getMessage.contains("not found in conversions"),
            "The error message was not correct"
          )
          assertEquals(topic, topicError, "The topic was not propagated")
        case _ => fail("The error was not propagated")
      }
    }
  }

  test(
    "if the conversion returns an event, it should be returned as part of the stream"
  ) {

    val topic = "TestTopic"
    val temp = 42.0
    val message = Message(topic, temp.toString.getBytes.toVector)
    val consumer = ConsumerStub(
      startConsumerStub = () => Stream(message)
    )

    val resultEventData =
      Event.Temperature.BatteryTemperatureMeasured(42.0)
    val converter: TopicMessagesConverter =
      Map(topic -> (_ => Right(resultEventData)))
    val sut = MqttToEventInputProcessor(consumer, converter)
    for {
      last <- sut.inputEventsStream.compile.last
    } yield {
      last match {
        case Some(
              Right(
                Event.Temperature.BatteryTemperatureMeasured(42.0)
              )
            ) =>
          () // Test passed
        case _ => fail("The event was not propagated")
      }
    }
  }

}
