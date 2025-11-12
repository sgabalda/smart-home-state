package calespiga.userinput

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.openhab.APIClient.ItemChangedEvent
import calespiga.openhab.ApiClientStub
import cats.effect.IO
import munit.CatsEffectSuite
import fs2.Stream
import calespiga.model.FanSignal

class UserInputManagerSuite extends CatsEffectSuite {

  test("on userInputEventsStream it should subscribe to the apiClient WS") {
    for {
      called <- IO.ref(false)
      apiClient = ApiClientStub(
        itemChangesStub =
          _ => Stream.eval(called.set(true)).flatMap(_ => Stream.empty)
      )
      sut = UserInputManager(apiClient)
      _ <- sut.userInputEventsStream().compile.drain
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
    val sut = UserInputManager(apiClient)
    for {
      last <- sut.userInputEventsStream().compile.last
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
    val itemsConverter: UserInputManager.OpenHabItemsConverter = Map()
    val sut = UserInputManager(apiClient, itemsConverter)
    for {
      last <- sut.userInputEventsStream().compile.last
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
    val itemsConverter: UserInputManager.OpenHabItemsConverter =
      Map("TestItem" -> (_ => Left(error)))
    val sut = UserInputManager(apiClient, itemsConverter)
    for {
      last <- sut.userInputEventsStream().compile.last
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
    val itemsConverter: UserInputManager.OpenHabItemsConverter =
      Map("TestItem" -> (_ => Right(resultEventData)))
    val sut = UserInputManager(apiClient, itemsConverter)
    for {
      last <- sut.userInputEventsStream().compile.last
    } yield {
      last match {
        case Some(
              Right(
                Event(
                  ts,
                  Event.Temperature.Fans
                    .BatteryFanCommand(FanSignal.TurnOn)
                )
              )
            ) =>
          assert(ts != null, "Timestamp was not set")
        case _ => fail("The event was not propagated")
      }
    }
  }

}
