package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.processor.utils.OfflineDetector
import calespiga.processor.ProcessorConfigHelper

class TemperaturesOfflineDetectorSuite extends FunSuite {
  val config = ProcessorConfigHelper.offlineDetectorConfig
  val id = "temperatures"
  val statusItem = "TemperaturesStatusItem"
  val now = Instant.parse("2023-08-17T10:00:00Z")

  val detector = TemperaturesOfflineDetector(config, id, statusItem)

  test("BatteryTemperatureMeasured sets online and schedules offline") {
    val state = State()
    val event = Event.Temperature.BatteryTemperatureMeasured(25.0)
    val (newState, actions) = detector.process(state, event, now)
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.onlineText),
      Action.Delayed(
        id + OfflineDetector.ID_SUFFIX,
        Action.SetUIItemValue(statusItem, config.offlineText),
        config.timeoutDuration
      )
    )
    assertEquals(newState, state)
    assertEquals(actions, expectedActions)
  }

  test("ElectronicsTemperatureMeasured sets online and schedules offline") {
    val state = State()
    val event = Event.Temperature.ElectronicsTemperatureMeasured(30.0)
    val (newState, actions) = detector.process(state, event, now)
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.onlineText),
      Action.Delayed(
        id + OfflineDetector.ID_SUFFIX,
        Action.SetUIItemValue(statusItem, config.offlineText),
        config.timeoutDuration
      )
    )
    assertEquals(newState, state)
    assertEquals(actions, expectedActions)
  }

  test("StartupEvent only schedules offline, does not set online") {
    val state = State()
    val event = Event.System.StartupEvent
    val (newState, actions) = detector.process(state, event, now)
    val expectedActions: Set[Action] = Set(
      Action.Delayed(
        id + OfflineDetector.ID_SUFFIX,
        Action.SetUIItemValue(statusItem, config.offlineText),
        config.timeoutDuration
      )
    )
    assertEquals(newState, state)
    assertEquals(actions, expectedActions)
  }

  test("Unrelated event does nothing") {
    val state = State()
    val event = Event.Heater.HeaterPowerStatusReported(
      calespiga.model.HeaterSignal.Power500
    )
    val (newState, actions) = detector.process(state, event, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
