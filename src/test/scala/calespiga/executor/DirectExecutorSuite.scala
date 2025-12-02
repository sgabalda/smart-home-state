package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.mqtt.ActionToMqttProducerStub
import calespiga.ui.UserInterfaceManagerStub
import cats.effect.IO
import munit.CatsEffectSuite
import scala.concurrent.duration.*

class DirectExecutorSuite extends CatsEffectSuite {

  test("Executor should request to the APIClient on SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    for {
      called <- IO.ref(false)
      uiManager = UserInterfaceManagerStub(
        updateUIItemStub = (_: String, _: String) => called.set(true)
      )
      executor = DirectExecutor(uiManager, ActionToMqttProducerStub())
      _ <- executor.execute(Set(Action.SetUIItemValue(item, value)))
      calledValue <- called.get
    } yield {
      assertEquals(calledValue, true, "APIClient was not called")
    }
  }

  test("Executor should return an error on failure of SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    val error = new Exception("API error")
    val action = Action.SetUIItemValue(item, value)

    DirectExecutor(
      UserInterfaceManagerStub(
        updateUIItemStub = (_: String, _: String) => IO.raiseError(error)
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

    val action = Action.SetUIItemValue(item, value)

    DirectExecutor(
      UserInterfaceManagerStub(
        updateUIItemStub = (_: String, _: String) => IO.unit
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
        actionToMqttStub = (_: Action.SendMqttStringMessage) => called.set(true)
      )
      executor = DirectExecutor(UserInterfaceManagerStub(), actionToMqtt)
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
      UserInterfaceManagerStub(),
      ActionToMqttProducerStub(
        actionToMqttStub =
          (_: Action.SendMqttStringMessage) => IO.raiseError(error)
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
      UserInterfaceManagerStub(),
      ActionToMqttProducerStub(
        actionToMqttStub = (_: Action.SendMqttStringMessage) => IO.unit
      )
    ).execute(Set(action)).map {
      case some :: _ => fail("The error was not propagated")
      case Nil       => // No error, as expected
    }
  }

  test(
    "Executor should call sendNotification on UserInterfaceManager for SendNotification action"
  ) {
    val notificationId = "notif-123"
    val message = "Test notification"
    val repeatInterval = Some(5.minutes)
    for {
      called <- IO.ref(Option.empty[(String, String, Option[FiniteDuration])])
      uiManager = UserInterfaceManagerStub(
        sendNotificationStub =
          (id: String, msg: String, repeat: Option[FiniteDuration]) =>
            called.set(Some((id, msg, repeat)))
      )
      executor = DirectExecutor(uiManager, ActionToMqttProducerStub())
      _ <- executor.execute(
        Set(Action.SendNotification(notificationId, message, repeatInterval))
      )
      calledValue <- called.get
    } yield {
      assertEquals(
        calledValue,
        Some((notificationId, message, repeatInterval)),
        "sendNotification was not called with the correct parameters"
      )
    }
  }
}
