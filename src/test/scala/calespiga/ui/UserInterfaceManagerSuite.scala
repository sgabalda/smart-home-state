package calespiga.ui

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.openhab.APIClient.ItemChangedEvent
import calespiga.openhab.ApiClientStub
import cats.effect.IO
import munit.CatsEffectSuite
import fs2.Stream
import calespiga.model.FanSignal
import calespiga.ui.UserInterfaceManager
import calespiga.config.UIConfig
import scala.concurrent.duration.*
import cats.effect.testkit.TestControl

class UserInterfaceManagerSuite extends CatsEffectSuite {

  private val uiConfig = UIConfig(
    notificationsItem = "NotificationsSHS",
    defaultRepeatInterval = 10.minutes,
    openHabConfig = ApiClientStub.config
  )

  test("sendNotification should call the APIClient with correct parameters") {
    val notificationId = "TestNotification"
    val message = "Test Message"

    for {
      called <- IO.ref[Option[(String, String)]](None)
      apiClient = ApiClientStub(
        changeItemStub =
          (item: String, value: String) => called.set(Some((item, value)))
      )
      sut <- UserInterfaceManager(
        apiClient,
        uiConfig
      )
      _ <- sut.sendNotification(notificationId, message, None)
      calledValue <- called.get
    } yield {
      assertEquals(
        calledValue,
        Some((uiConfig.notificationsItem, message)),
        "APIClient was not called with correct parameters"
      )
    }
  }

  test(
    "sendNotification should call the APIClient if within repeat interval and different ID"
  ) {
    val notificationId = "TestNotification"
    val differentNotificationId = "DifferentNotification"
    val message = "Test Message"
    val repeatInterval = 5.minutes

    val program = for {
      called <- IO.ref[Int](0)
      apiClient = ApiClientStub(
        changeItemStub = (_: String, _: String) => called.update(_ + 1)
      )
      sut <- UserInterfaceManager(
        apiClient,
        uiConfig
      )
      _ <- sut.sendNotification(notificationId, message, Some(repeatInterval))
      calledValue1 <- called.get
      _ <- IO.sleep(2.minutes)
      _ <- sut.sendNotification(
        differentNotificationId,
        message,
        Some(repeatInterval)
      )
      calledValue2 <- called.get
    } yield {
      assertEquals(
        calledValue1,
        1,
        "APIClient was not called on a first call"
      )
      assertEquals(
        calledValue2,
        2,
        "APIClient was NOT called on a second call within the repeat interval but different ID"
      )
    }

    TestControl.executeEmbed(program)
  }

  test(
    "sendNotification should again call the APIClient if after the repeat interval and same ID, but not before"
  ) {
    val notificationId = "TestNotification"
    val message = "Test Message"
    val repeatInterval = 5.minutes

    val program = for {
      called <- IO.ref[Int](0)
      apiClient = ApiClientStub(
        changeItemStub = (_: String, _: String) => called.update(_ + 1)
      )
      sut <- UserInterfaceManager(
        apiClient,
        uiConfig
      )
      _ <- sut.sendNotification(notificationId, message, Some(repeatInterval))
      calledValue1 <- called.get
      _ <- IO.sleep(2.minutes)
      _ <- sut.sendNotification(notificationId, message, Some(repeatInterval))
      calledValue2 <- called.get
      _ <- IO.sleep(4.minutes)
      _ <- sut.sendNotification(notificationId, message, Some(repeatInterval))
      calledValue3 <- called.get
    } yield {
      assertEquals(
        calledValue1,
        1,
        "APIClient was not called on a first call"
      )
      assertEquals(
        calledValue2,
        1,
        "APIClient was called on a second call within the repeat interval"
      )
      assertEquals(
        calledValue3,
        2,
        "APIClient was NOT called on a third call after the repeat interval"
      )
    }

    TestControl.executeEmbed(program)
  }

  test(
    "sendNotification call again the APIClient using default repeat interval"
  ) {
    val notificationId = "TestNotification"
    val message = "Test Message"

    val program = for {
      called <- IO.ref[Int](0)
      apiClient = ApiClientStub(
        changeItemStub = (_: String, _: String) => called.update(_ + 1)
      )
      sut <- UserInterfaceManager(
        apiClient,
        uiConfig
      )
      _ <- sut.sendNotification(notificationId, message, None)
      calledValue1 <- called.get
      _ <- IO.sleep(6.minutes)
      _ <- sut.sendNotification(notificationId, message, None)
      calledValue2 <- called.get
      _ <- IO.sleep(5.minutes)
      _ <- sut.sendNotification(notificationId, message, None)
      calledValue3 <- called.get
    } yield {
      assertEquals(
        calledValue1,
        1,
        "APIClient was not called on a first call"
      )
      assertEquals(
        calledValue2,
        1,
        "APIClient was called on a second call within the repeat interval"
      )
      assertEquals(
        calledValue3,
        2,
        "APIClient was NOT called on a third call after the repeat interval"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("on userInputEventsStream it should subscribe to the apiClient WS") {
    for {
      called <- IO.ref(false)
      apiClient = ApiClientStub(
        itemChangesStub =
          _ => Stream.eval(called.set(true)).flatMap(_ => Stream.empty)
      )
      sut <- UserInterfaceManager(apiClient, uiConfig)
      _ <- sut.userInputEventsStream.compile.drain
      calledValue <- called.get
    } yield {
      assertEquals(calledValue, true, "APIClient was not called")
    }
  }

  test("if the APIClient returns an error, it should be propagated") {
    val error = new Exception("API error")
    val expectedError = ErrorManager.Error.OpenHabInputError(error)
    val apiClient = ApiClientStub(
      itemChangesStub = _ => Stream(Left(error))
    )
    for {
      sut <- UserInterfaceManager(apiClient, uiConfig)
      last <- sut.userInputEventsStream.compile.last
    } yield {
      assertEquals(
        last,
        Some(Left(expectedError)),
        "APIClient error was not propagated"
      )
    }
  }

  test(
    "if the APIClient returns an item not in the converter, an error should be propagated"
  ) {
    val itemInfo = ItemChangedEvent("TestItem", "TestValue")
    val apiClient = ApiClientStub(
      itemChangesStub = _ => Stream(Right(itemInfo))
    )
    val itemsConverter: UserInterfaceManager.OpenHabItemsConverter = Map()
    for {
      sut <- UserInterfaceManager(apiClient, uiConfig, itemsConverter)
      last <- sut.userInputEventsStream.compile.last
    } yield {
      last match {
        case Some(Left(ErrorManager.Error.OpenHabInputError(e))) =>
          assert(
            e.getMessage.contains("Item not found for conversion"),
            "The error message was not correct"
          )
        case _ => fail("The error was not propagated")
      }
    }
  }

  test("if the conversion returns an error, it should be propagated") {
    val itemInfo = ItemChangedEvent("TestItem", "TestValue")
    val apiClient = ApiClientStub(
      itemChangesStub = _ => Stream(Right(itemInfo))
    )
    val error = new Exception("Conversion error")
    val expectedError = ErrorManager.Error.OpenHabInputError(error)
    val itemsConverter: UserInterfaceManager.OpenHabItemsConverter =
      Map("TestItem" -> (_ => Left(error)))
    for {
      sut <- UserInterfaceManager(apiClient, uiConfig, itemsConverter)
      last <- sut.userInputEventsStream.compile.last
    } yield {
      assertEquals(
        last,
        Some(Left(expectedError)),
        "Conversion error was not propagated"
      )
    }
  }

  test(
    "if the conversion returns an event, it should be returned as part of the stream"
  ) {
    val itemInfo = ItemChangedEvent("TestItem", "TestValue")
    val apiClient = ApiClientStub(
      itemChangesStub = _ => Stream(Right(itemInfo))
    )
    val resultEventData =
      Event.Temperature.Fans.BatteryFanCommand(FanSignal.TurnOn)
    val itemsConverter: UserInterfaceManager.OpenHabItemsConverter =
      Map("TestItem" -> (_ => Right(resultEventData)))
    for {
      sut <- UserInterfaceManager(apiClient, uiConfig, itemsConverter)
      last <- sut.userInputEventsStream.compile.last
    } yield {
      last match {
        case Some(
              Right(
                Event.Temperature.Fans
                  .BatteryFanCommand(FanSignal.TurnOn)
              )
            ) =>
          () // Test passed
        case _ => fail("The event was not propagated")
      }
    }
  }

}
