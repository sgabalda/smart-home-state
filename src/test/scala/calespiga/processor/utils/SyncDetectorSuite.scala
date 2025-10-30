package calespiga.processor.utils

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import scala.concurrent.duration._

class SyncDetectorSuite extends FunSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val id = "test-sync"
  val statusItem = "TestSyncStatusItem"

  val config = SyncDetectorConfig(
    timeoutDuration = 30.seconds,
    syncText = "SYNC",
    syncingText = "SYNCING",
    nonSyncText = "NON_SYNC"
  )

  // Use real State fields: heater.status and heater.lastCommandSent, and heater.lastSyncing
  def field1ToCheck(
      state: State
  ): Option[calespiga.model.HeaterSignal.ControllerState] = state.heater.status
  def field2ToCheck(
      state: State
  ): Option[calespiga.model.HeaterSignal.ControllerState] =
    state.heater.lastCommandSent
  def getLastSyncing(state: State): Option[Instant] = state.heater.lastSyncing
  def setLastSyncing(state: State, v: Option[Instant]): State =
    state.copy(heater = state.heater.copy(lastSyncing = v))

  val detector = calespiga.processor.SyncDetector(
    config,
    id,
    field1ToCheck,
    field2ToCheck,
    getLastSyncing,
    setLastSyncing,
    statusItem
  )

  val dummyEvent = Event.Temperature.BatteryTemperatureMeasured(0.0)

  test(
    "Already in sync: sets SYNC and cancels delayed action, clears lastSyncing"
  ) {
    import calespiga.model.HeaterSignal
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power500),
        lastSyncing = Some(now)
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.heater.lastSyncing, None)
    val expectedActions = Set(
      Action.SetOpenHabItemValue(statusItem, config.syncText),
      Action.Cancel(id + calespiga.processor.SyncDetector.ID_SUFFIX)
    )
    assertEquals(actions, expectedActions)
  }

  test(
    "Not in sync, first time: sets SYNCING, schedules delayed NON_SYNC, sets lastSyncing"
  ) {
    import calespiga.model.HeaterSignal
    val state = State().copy(heater =
      State.Heater(
        status = Some(HeaterSignal.Power500),
        lastCommandSent = Some(HeaterSignal.Power1000),
        lastSyncing = None
      )
    )
    val (newState, actions) = detector.process(state, dummyEvent, now)
    assertEquals(newState.heater.lastSyncing, Some(now))
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
    import calespiga.model.HeaterSignal
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
}
