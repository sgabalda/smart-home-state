package calespiga.processor.heater

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import scala.concurrent.duration._
import calespiga.model.HeaterSignal

class HeaterSyncDetectorSuite extends FunSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val id = "heater-sync"
  val statusItem = "HeaterSyncStatusItem"

  val config = SyncDetectorConfig(
    timeoutDuration = 30.seconds,
    syncText = "SYNC",
    syncingText = "SYNCING",
    nonSyncText = "NON_SYNC"
  )

  val detector = HeaterSyncDetector(config, id, statusItem)
  val dummyEvent = Event.Heater.HeaterPowerStatusReported(HeaterSignal.Power500)
  val nonRelevantEvent = Event.Temperature.BatteryTemperatureMeasured(0.0)

  test(
    "Already in sync: sets SYNC and cancels delayed action, clears lastSyncing"
  ) {

    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power500),
        lastSyncing = Some(now)
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.heater.status, Some(HeaterSignal.Power500))
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power500))
    assertEquals(newState.heater.lastSyncing, None)
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.syncText),
      Action.Cancel(id + calespiga.processor.SyncDetector.ID_SUFFIX)
    )
    assertEquals(actions, expectedActions)
  }

  test("Already in sync and no syncing time: do nothing") {
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power500),
        lastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "Not in sync, first time: sets SYNCING, schedules delayed NON_SYNC, sets lastSyncing"
  ) {
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power1000),
        lastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.heater.status, Some(HeaterSignal.Power500))
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power1000))
    assertEquals(newState.heater.lastSyncing, Some(now))
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.syncingText),
      Action.Delayed(
        id + calespiga.processor.SyncDetector.ID_SUFFIX,
        Action.SetUIItemValue(statusItem, config.nonSyncText),
        config.timeoutDuration
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("Not in sync, already syncing: does nothing") {
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power1000),
        lastSyncing = Some(now.minusSeconds(10))
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test("Non-relevant event: does nothing") {
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power1000),
        lastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, nonRelevantEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
