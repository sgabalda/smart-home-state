package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.mqtt.ActionToMqttProducerStub
import calespiga.openhab.ApiClientStub
import cats.effect.IO
import munit.CatsEffectSuite

class DirectExecutorSuite extends CatsEffectSuite {

  test("Executor should request to the APIClient on SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    for {
      called <- IO.ref(false)
      apiClient = ApiClientStub(
        changeItemStub = (item: String, value: String) => called.set(true)
      )
      executor = DirectExecutor(apiClient, ActionToMqttProducerStub())
      _ <- executor.execute(Set(Action.SetOpenHabItemValue(item, value)))
      calledValue <- called.get
    } yield {
      assertEquals(calledValue, true, "APIClient was not called")
    }
  }

  test("Executor should return an error on failure of SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    val error = new Exception("API error")
    val action = Action.SetOpenHabItemValue(item, value)

    DirectExecutor(
      ApiClientStub(
        changeItemStub = (item: String, value: String) => IO.raiseError(error)
      ),
      ActionToMqttProducerStub()
    ).execute(Set(action)).map {
      case List(ErrorManager.Error.ExecutionError(throwable, act)) =>
        assertEquals(throwable, error, "The throwable was not propagated")
        assertEquals(act, action, "The action was not propagated")
      case _ => fail("The error was not propagated")
    }
  }

  test("Executor should return no error on success of SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    val action = Action.SetOpenHabItemValue(item, value)

    DirectExecutor(
      ApiClientStub(
        changeItemStub = (item: String, value: String) => IO.unit
      ),
      ActionToMqttProducerStub()
    ).execute(Set(action)).map {
      case some :: _ => fail("The error was not propagated")
      case Nil       => // No error, as expected
    }
  }

  test(
    "Executor should request to the ActionToMqttProducer on SendMqttStringMessage"
  ) {

    val action = Action.SendMqttStringMessage(
      topic = "TestTopic",
      message = "TestMessage"
    )

    for {
      called <- IO.ref(false)
      actionToMqtt = ActionToMqttProducerStub(
        actionToMqttStub =
          (action: Action.SendMqttStringMessage) => called.set(true)
      )
      executor = DirectExecutor(ApiClientStub(), actionToMqtt)
      _ <- executor.execute(Set(action))
      calledValue <- called.get
    } yield {
      assertEquals(calledValue, true, "ActionToMqttProducer was not called")
    }
  }

  test("Executor should return an error on failure of SendMqttStringMessage") {

    val error = new Exception("Mqtt error")
    val action = Action.SendMqttStringMessage(
      topic = "TestTopic",
      message = "TestMessage"
    )

    DirectExecutor(
      ApiClientStub(),
      ActionToMqttProducerStub(
        actionToMqttStub =
          (action: Action.SendMqttStringMessage) => IO.raiseError(error)
      )
    ).execute(Set(action)).map {
      case List(ErrorManager.Error.ExecutionError(throwable, act)) =>
        assertEquals(throwable, error, "The throwable was not propagated")
        assertEquals(act, action, "The action was not propagated")
      case _ => fail("The error was not propagated")
    }
  }

  test("Executor should return no error on success of SendMqttStringMessage") {

    val action = Action.SendMqttStringMessage(
      topic = "TestTopic",
      message = "TestMessage"
    )

    DirectExecutor(
      ApiClientStub(),
      ActionToMqttProducerStub(
        actionToMqttStub = (action: Action.SendMqttStringMessage) => IO.unit
      )
    ).execute(Set(action)).map {
      case some :: _ => fail("The error was not propagated")
      case Nil       => // No error, as expected
    }
  }
}
