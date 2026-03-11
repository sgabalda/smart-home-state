package calespiga.processor.grid

import munit.FunSuite
import calespiga.model.{Action, Event, State}
import calespiga.model.GridSignal
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant

class GridConnectionProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.gridConfig

  /** A controllable stub for GridConnectionManager that records calls and
    * returns a preconfigured result.
    */
  private class ManagerStub(
      requestResult: (State, Set[Action]) = (State(), Set.empty),
      releaseResult: (State, Set[Action]) = (State(), Set.empty),
      applyResult: (State, Set[Action]) = (State(), Set.empty)
  ) extends GridConnectionManager {
    var requestCalls: List[GridSignal.ActorsConnecting] = Nil
    var releaseCalls: List[GridSignal.ActorsConnecting] = Nil
    var applyCalls: Int = 0

    override def requestConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      requestCalls = requestCalls :+ actor
      requestResult
    }

    override def releaseConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      releaseCalls = releaseCalls :+ actor
      releaseResult
    }

    override def applyConnection(state: State): (State, Set[Action]) = {
      applyCalls += 1
      applyResult
    }
  }

  private def mqttAction(cmd: GridSignal.ControllerState): Action =
    Action.SendMqttStringMessage(
      config.mqttTopicForCommand,
      GridSignal.toMqttCommand(cmd)
    )

  // ── GridConnectionStatusReported ────────────────────────────────────────────

  test("GridConnectionStatusReported updates grid status and sets UI item") {
    val stub = ManagerStub()
    val processor = GridConnectionProcessor(config, stub)
    val state = State()
    val event = Event.Grid.GridConnectionStatusReported(GridSignal.Connected)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(newState.grid.status, Some(GridSignal.Connected))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "on"))
    )
    // manager must not be involved for status reports
    assertEquals(stub.requestCalls, Nil)
    assertEquals(stub.releaseCalls, Nil)
  }

  test("GridConnectionStatusReported with Disconnected sets status to off") {
    val stub = ManagerStub()
    val processor = GridConnectionProcessor(config, stub)
    val state = State().modify(_.grid.status).setTo(Some(GridSignal.Connected))
    val event = Event.Grid.GridConnectionStatusReported(GridSignal.Disconnected)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(newState.grid.status, Some(GridSignal.Disconnected))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "off"))
    )
  }

  // ── GridManualConnectionChanged ──────────────────────────────────────────────

  test(
    "GridManualConnectionChanged(true) delegates to manager.requestConnection with Manual"
  ) {
    val expectedState =
      State().modify(_.grid.lastCommandSent).setTo(Some(GridSignal.Connected))
    val expectedActions = Set[Action](mqttAction(GridSignal.Connected))
    val stub = ManagerStub(requestResult = (expectedState, expectedActions))
    val processor = GridConnectionProcessor(config, stub)

    val (newState, actions) = processor.process(
      State(),
      Event.Grid.GridManualConnectionChanged(true),
      now
    )

    assertEquals(stub.requestCalls, List(GridSignal.Manual))
    assertEquals(stub.releaseCalls, Nil)
    assertEquals(newState, expectedState)
    assertEquals(actions, expectedActions)
  }

  test(
    "GridManualConnectionChanged(false) delegates to manager.releaseConnection with Manual"
  ) {
    val expectedState = State()
      .modify(_.grid.lastCommandSent)
      .setTo(Some(GridSignal.Disconnected))
    val expectedActions = Set[Action](mqttAction(GridSignal.Disconnected))
    val stub = ManagerStub(releaseResult = (expectedState, expectedActions))
    val processor = GridConnectionProcessor(config, stub)

    val (newState, actions) = processor.process(
      State(),
      Event.Grid.GridManualConnectionChanged(false),
      now
    )

    assertEquals(stub.releaseCalls, List(GridSignal.Manual))
    assertEquals(stub.requestCalls, Nil)
    assertEquals(newState, expectedState)
    assertEquals(actions, expectedActions)
  }

  // ── StartupEvent ─────────────────────────────────────────────────────────────

  test(
    "StartupEvent calls manager.applyConnection and adds actions returned"
  ) {
    val managerState = State()
      .modify(_.grid.lastCommandSent)
      .setTo(Some(GridSignal.Disconnected))
    val managerActions = Set[Action](mqttAction(GridSignal.Disconnected))
    val stub = ManagerStub(applyResult = (managerState, managerActions))
    val processor = GridConnectionProcessor(config, stub)
    val initialState = State()

    val (newState, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assertEquals(stub.applyCalls, 1)
    assertEquals(newState, managerState)
    assert(
      actions.contains(Action.SetUIItemValue(config.manualSwitchItem, "false"))
    )
    assert(managerActions.subsetOf(actions))
  }

  test(
    "StartupEvent sets manualSwitch UI item to true when Manual is in devicesRequestedConnection"
  ) {
    val state = State()
      .modify(_.grid.devicesRequestedConnection)
      .setTo(Set(GridSignal.Manual))
    val stub = ManagerStub(applyResult = (state, Set.empty))
    val processor = GridConnectionProcessor(config, stub)

    val (_, actions) = processor.process(state, Event.System.StartupEvent, now)

    assert(
      actions.contains(Action.SetUIItemValue(config.manualSwitchItem, "true"))
    )
  }

  // ── Unrelated events ─────────────────────────────────────────────────────────

  test("Unrelated events return unchanged state and empty actions") {
    val stub = ManagerStub()
    val processor = GridConnectionProcessor(config, stub)
    val state = State()

    val (newState, actions) = processor.process(
      state,
      Event.FeatureFlagEvents.SetHeaterManagement(false),
      now
    )

    assertEquals(newState, state)
    assertEquals(actions, Set.empty[Action])
    assertEquals(stub.requestCalls, Nil)
    assertEquals(stub.releaseCalls, Nil)
    assertEquals(stub.applyCalls, 0)
  }
}
