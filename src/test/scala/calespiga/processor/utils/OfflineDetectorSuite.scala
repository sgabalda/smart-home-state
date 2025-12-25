package calespiga.processor.utils

import calespiga.processor.utils.OfflineDetector

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import scala.concurrent.duration._

class OfflineDetectorSuite extends FunSuite {

  val originalId = "test"
  val id = originalId + OfflineDetector.ID_SUFFIX
  val statusItem = "TestStatusItem"
  val now = Instant.parse("2023-08-17T10:00:00Z")

  // Dummy config for testing
  val config = OfflineDetectorConfig(
    timeoutDuration = 30.seconds,
    onlineText = "ONLINE",
    offlineText = "OFFLINE"
  )

  // Use an existing event type for matching, e.g. BatteryTemperatureMeasured
  val matchingEvent = Event.Temperature.BatteryTemperatureMeasured(42.0)
  val matcher: Event.EventData => Boolean = {
    case Event.Temperature.BatteryTemperatureMeasured(_) => true
    case _                                               => false
  }

  val detector = OfflineDetector(config, originalId, matcher, statusItem)

  test("Matching event sets online and schedules offline") {
    val state = State()
    val (newState, actions) = detector.process(state, matchingEvent, now)
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.onlineText),
      Action.Delayed(
        id,
        Action.SetUIItemValue(statusItem, config.offlineText),
        config.timeoutDuration
      )
    )
    assertEquals(newState, state)
    assertEquals(actions, expectedActions)
  }

  test("StartupEvent only schedules offline, does not set online") {
    val state = State()
    val (newState, actions) =
      detector.process(state, Event.System.StartupEvent, now)
    val expectedActions: Set[Action] = Set(
      Action.Delayed(
        id,
        Action.SetUIItemValue(statusItem, config.offlineText),
        config.timeoutDuration
      )
    )
    assertEquals(newState, state)
    assertEquals(actions, expectedActions)
  }

  test("Unrelated event does nothing") {
    val state = State()
    val unrelatedEvent = Event.Temperature.BatteryClosetTemperatureMeasured(0.0)
    val (newState, actions) = detector.process(state, unrelatedEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
