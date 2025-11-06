package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import scala.concurrent.duration._

class BatteryFanSyncDetectorSuite extends FunSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val id = "battery-fan-sync"
  val statusItem = "BatteryFanSyncStatusItem"

  val config = SyncDetectorConfig(
    timeoutDuration = 30.seconds,
    syncText = "SYNC",
    syncingText = "SYNCING",
    nonSyncText = "NON_SYNC"
  )

  val detector = BatteryFanSyncDetector(config, id, statusItem)
  val dummyEvent =
    Event.Temperature.Fans.BatteryFanStatus(calespiga.model.FanSignal.On)
  val nonRelevantEvent = Event.Temperature.BatteryTemperatureMeasured(0.0)

  test(
    "Already in sync: sets SYNC and cancels delayed action, clears lastSyncing"
  ) {
    val state = State().copy(fans =
      State.Fans(
        fanBatteriesStatus = calespiga.model.FanSignal.On,
        fanBatteriesLatestCommandSent = Some(calespiga.model.FanSignal.On),
        fanBatteriesLastSyncing = Some(now)
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.fans.fanBatteriesStatus, calespiga.model.FanSignal.On)
    assertEquals(
      newState.fans.fanBatteriesLatestCommandSent,
      Some(calespiga.model.FanSignal.On)
    )
    assertEquals(newState.fans.fanBatteriesLastSyncing, None)
    val expectedActions = Set(
      Action.SetOpenHabItemValue(statusItem, config.syncText),
      Action.Cancel(id + calespiga.processor.SyncDetector.ID_SUFFIX)
    )
    assertEquals(actions, expectedActions)
  }

  test("Already in sync and no syncing time: do nothing") {
    val state = State().copy(fans =
      State.Fans(
        fanBatteriesStatus = calespiga.model.FanSignal.On,
        fanBatteriesLatestCommandSent = Some(calespiga.model.FanSignal.On),
        fanBatteriesLastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "Not in sync, first time: sets SYNCING, schedules delayed NON_SYNC, sets lastSyncing"
  ) {
    val state = State().copy(fans =
      State.Fans(
        fanBatteriesStatus = calespiga.model.FanSignal.On,
        fanBatteriesLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanBatteriesLastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.fans.fanBatteriesStatus, calespiga.model.FanSignal.On)
    assertEquals(
      newState.fans.fanBatteriesLatestCommandSent,
      Some(calespiga.model.FanSignal.Off)
    )
    assertEquals(newState.fans.fanBatteriesLastSyncing, Some(now))
    val expectedActions = Set(
      Action.SetOpenHabItemValue(statusItem, config.syncingText),
      Action.Delayed(
        id + calespiga.processor.SyncDetector.ID_SUFFIX,
        Action.SetOpenHabItemValue(statusItem, config.nonSyncText),
        config.timeoutDuration
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("Not in sync, already syncing: does nothing") {
    val state = State().copy(fans =
      State.Fans(
        fanBatteriesStatus = calespiga.model.FanSignal.On,
        fanBatteriesLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanBatteriesLastSyncing = Some(now.minusSeconds(10))
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test("Non-relevant event: does nothing") {
    val state = State().copy(fans =
      State.Fans(
        fanBatteriesStatus = calespiga.model.FanSignal.On,
        fanBatteriesLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanBatteriesLastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, nonRelevantEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
