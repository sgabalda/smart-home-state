package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import scala.concurrent.duration._

class ElectronicsFanSyncDetectorSuite extends FunSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val id = "electronics-fan-sync"
  val statusItem = "ElectronicsFanSyncStatusItem"

  val config = SyncDetectorConfig(
    timeoutDuration = 30.seconds,
    syncText = "SYNC",
    syncingText = "SYNCING",
    nonSyncText = "NON_SYNC"
  )

  val detector = ElectronicsFanSyncDetector(config, id, statusItem)
  val dummyEvent =
    Event.Temperature.Fans.ElectronicsFanStatus(calespiga.model.FanSignal.On)
  val nonRelevantEvent = Event.Temperature.BatteryTemperatureMeasured(0.0)

  test(
    "Already in sync: sets SYNC and cancels delayed action, clears lastSyncing"
  ) {
    val state = State().copy(fans =
      State.Fans(
        fanElectronicsStatus = calespiga.model.FanSignal.On,
        fanElectronicsLatestCommandSent = Some(calespiga.model.FanSignal.On),
        fanElectronicsLastSyncing = Some(now)
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(
      newState.fans.fanElectronicsStatus,
      calespiga.model.FanSignal.On
    )
    assertEquals(
      newState.fans.fanElectronicsLatestCommandSent,
      Some(calespiga.model.FanSignal.On)
    )
    assertEquals(newState.fans.fanElectronicsLastSyncing, None)
    val expectedActions = Set(
      Action.SetUIItemValue(statusItem, config.syncText),
      Action.Cancel(id + calespiga.processor.SyncDetector.ID_SUFFIX)
    )
    assertEquals(actions, expectedActions)
  }

  test("Already in sync and no syncing time: do nothing") {
    val state = State().copy(fans =
      State.Fans(
        fanElectronicsStatus = calespiga.model.FanSignal.On,
        fanElectronicsLatestCommandSent = Some(calespiga.model.FanSignal.On),
        fanElectronicsLastSyncing = None
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
        fanElectronicsStatus = calespiga.model.FanSignal.On,
        fanElectronicsLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanElectronicsLastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(
      newState.fans.fanElectronicsStatus,
      calespiga.model.FanSignal.On
    )
    assertEquals(
      newState.fans.fanElectronicsLatestCommandSent,
      Some(calespiga.model.FanSignal.Off)
    )
    assertEquals(newState.fans.fanElectronicsLastSyncing, Some(now))
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
    val state = State().copy(fans =
      State.Fans(
        fanElectronicsStatus = calespiga.model.FanSignal.On,
        fanElectronicsLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanElectronicsLastSyncing = Some(now.minusSeconds(10))
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test("Non-relevant event: does nothing") {
    val state = State().copy(fans =
      State.Fans(
        fanElectronicsStatus = calespiga.model.FanSignal.On,
        fanElectronicsLatestCommandSent = Some(calespiga.model.FanSignal.Off),
        fanElectronicsLastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, nonRelevantEvent, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
