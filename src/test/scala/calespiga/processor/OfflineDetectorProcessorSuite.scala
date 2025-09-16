package calespiga.processor

import munit.FunSuite
import calespiga.model.{Action, Event, State, Switch}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import scala.concurrent.duration._

class OfflineDetectorProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val testState = calespiga.model.Fixture.state

  private val testConfig = OfflineDetectorConfig(
    timeoutDuration = 5.minutes,
    temperaturesStatusItem = "TemperaturesMicroControladorEstat",
    onlineText = "En línia",
    offlineText = "Fora de línia"
  )

  test(
    "OfflineDetectorProcessor should schedule offline timeout on StartupEvent"
  ) {
    val sut = OfflineDetectorProcessor(testConfig)

    val (resultState, resultActions) = sut.process(
      testState,
      Event.System.StartupEvent,
      now
    )

    assertEquals(
      resultState,
      testState,
      "State should remain unchanged"
    )

    assertEquals(resultActions.size, 1, "Should produce exactly one action")

    val delayedAction = resultActions.collectFirst { case a: Action.Delayed =>
      a
    }
    assert(delayedAction.isDefined, "Should produce a Delayed action")
    assertEquals(delayedAction.get.id, "temperatures-offline-timeout")

    delayedAction.get.action match {
      case Action.SetOpenHabItemValue(item, value) =>
        assertEquals(item, testConfig.temperaturesStatusItem)
        assertEquals(value, testConfig.offlineText)
      case _ => fail("Delayed action should be SetOpenHabItemValue to offline")
    }
  }

  test(
    "OfflineDetectorProcessor should set online and reschedule timeout on temperature events"
  ) {
    val sut = OfflineDetectorProcessor(testConfig)

    val temperatureEvents = List(
      Event.Temperature.BatteryTemperatureMeasured(25.0),
      Event.Temperature.BatteryClosetTemperatureMeasured(22.0),
      Event.Temperature.ElectronicsTemperatureMeasured(30.0),
      Event.Temperature.ExternalTemperatureMeasured(15.0)
    )

    temperatureEvents.foreach { eventData =>
      val (resultState, resultActions) = sut.process(
        testState,
        eventData,
        now
      )

      assertEquals(
        resultState,
        testState,
        s"State should remain unchanged for event: $eventData"
      )

      assertEquals(
        resultActions.size,
        2,
        s"Should produce exactly 2 actions for event: $eventData"
      )

      // Check for set online action
      val setOnlineAction = resultActions.collectFirst {
        case Action.SetOpenHabItemValue(item, testConfig.onlineText)
            if item == testConfig.temperaturesStatusItem =>
          item
      }
      assert(
        setOnlineAction.isDefined,
        s"Should set status to online for event: $eventData"
      )

      // Check for new delayed action (automatically cancels previous one)
      val delayedAction = resultActions.collectFirst { case a: Action.Delayed =>
        a
      }
      assert(
        delayedAction.isDefined,
        s"Should schedule new timeout for event: $eventData"
      )
      assertEquals(delayedAction.get.id, "temperatures-offline-timeout")

      // Verify the delayed action sets status to OFFLINE
      delayedAction.get.action match {
        case Action.SetOpenHabItemValue(item, value) =>
          assertEquals(
            item,
            testConfig.temperaturesStatusItem,
            s"Delayed action should target correct item for event: $eventData"
          )
          assertEquals(
            value,
            testConfig.offlineText,
            s"Delayed action should set status to offline for event: $eventData"
          )
        case _ =>
          fail(
            s"Delayed action should be SetOpenHabItemValue to offline for event: $eventData"
          )
      }
    }
  }

  test(
    "OfflineDetectorProcessor should set online and reschedule timeout on fan events"
  ) {
    val sut = OfflineDetectorProcessor(testConfig)

    val fanEvents = List(
      Event.Temperature.Fans.BatteryFanSwitchReported(Switch.On),
      Event.Temperature.Fans.BatteryFanSwitchReported(Switch.Off),
      Event.Temperature.Fans.ElectronicsFanSwitchReported(Switch.On),
      Event.Temperature.Fans.ElectronicsFanSwitchReported(Switch.Off)
    )

    fanEvents.foreach { eventData =>
      val (resultState, resultActions) = sut.process(
        testState,
        eventData,
        now
      )

      assertEquals(
        resultState,
        testState,
        s"State should remain unchanged for event: $eventData"
      )

      assertEquals(
        resultActions.size,
        2,
        s"Should produce exactly 2 actions for event: $eventData"
      )

      // Check for set online action
      val setOnlineAction = resultActions.collectFirst {
        case Action.SetOpenHabItemValue(item, testConfig.onlineText)
            if item == testConfig.temperaturesStatusItem =>
          item
      }
      assert(
        setOnlineAction.isDefined,
        s"Should set status to online for event: $eventData"
      )

      // Check for new delayed action (automatically cancels previous one)
      val delayedAction = resultActions.collectFirst { case a: Action.Delayed =>
        a
      }
      assert(
        delayedAction.isDefined,
        s"Should schedule new timeout for event: $eventData"
      )
      assertEquals(delayedAction.get.id, "temperatures-offline-timeout")

      // Verify the delayed action sets status to OFFLINE
      delayedAction.get.action match {
        case Action.SetOpenHabItemValue(item, value) =>
          assertEquals(
            item,
            testConfig.temperaturesStatusItem,
            s"Delayed action should target correct item for event: $eventData"
          )
          assertEquals(
            value,
            testConfig.offlineText,
            s"Delayed action should set status to offline for event: $eventData"
          )
        case _ =>
          fail(
            s"Delayed action should be SetOpenHabItemValue to offline for event: $eventData"
          )
      }
    }
  }

  test("OfflineDetectorProcessor should ignore non-microcontroller events") {
    val sut = OfflineDetectorProcessor(testConfig)

    val nonMicrocontrollerEvents = List(
      Event.Temperature.GoalTemperatureChanged(21.0),
      Event.Temperature.Fans.FanManagementChanged(Switch.On),
      Event.Temperature.Fans.BatteryFanSwitchManualChanged(Switch.On),
      Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(Switch.Off)
    )

    nonMicrocontrollerEvents.foreach { eventData =>
      val (resultState, resultActions) = sut.process(
        testState,
        eventData,
        now
      )

      assertEquals(
        resultState,
        testState,
        s"State should remain unchanged for event: $eventData"
      )
      assertEquals(
        resultActions,
        Set.empty[Action],
        s"No actions should be produced for non-microcontroller event: $eventData"
      )
    }
  }

}
