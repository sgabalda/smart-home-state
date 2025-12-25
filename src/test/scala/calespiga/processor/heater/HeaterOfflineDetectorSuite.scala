package calespiga.processor.heater

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import scala.concurrent.duration._
import calespiga.model.HeaterSignal
import calespiga.processor.utils.OfflineDetector

class HeaterOfflineDetectorSuite extends FunSuite {
  val config = OfflineDetectorConfig(
    timeoutDuration = 30.seconds,
    onlineText = "ONLINE",
    offlineText = "OFFLINE"
  )
  val id = "heater"
  val statusItem = "HeaterStatusItem"
  val now = Instant.parse("2023-08-17T10:00:00Z")

  val detector = HeaterOfflineDetector(config, id, statusItem)

  test("HeaterIsHotReported sets online and schedules offline") {
    val state = State()
    val event = Event.Heater.HeaterIsHotReported(HeaterSignal.Hot)
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

  test("HeaterPowerStatusReported sets online and schedules offline") {
    val state = State()
    val event = Event.Heater.HeaterPowerStatusReported(
      calespiga.model.HeaterSignal.Power500
    )
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
    val event = Event.Temperature.BatteryClosetTemperatureMeasured(0.0)
    val (newState, actions) = detector.process(state, event, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
