package calespiga.processor.grid

import munit.FunSuite
import calespiga.model.{Action, State}
import calespiga.model.GridSignal
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*

class GridConnectionManagerSuite extends FunSuite {

  private val config = ProcessorConfigHelper.gridConfig

  private def expectedActions(
      cmd: GridSignal.ControllerState,
      state: State
  ): Set[Action] =
    Set(
      Action.SendMqttStringMessage(
        config.mqttTopicForCommand,
        GridSignal.toMqttCommand(cmd)
      ),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          GridSignal.toMqttCommand(cmd)
        ),
        config.resendInterval
      ),
      Action.SetUIItemValue(
        config.reasonItem,
        state.grid.devicesRequestedConnection
          .map(_.toString)
          .toSeq
          .sorted
          .mkString(",")
      )
    )

  private val manager = GridConnectionManager(config)

  test("requestConnection adds actor and sends Connected command") {
    val state = State()
    val (newState, actions) =
      manager.requestConnection(GridSignal.Manual, state)

    assertEquals(
      newState.grid.devicesRequestedConnection,
      Set[GridSignal.ActorsConnecting](GridSignal.Manual)
    )
    assertEquals(newState.grid.lastCommandSent, Some(GridSignal.Connected))
    assertEquals(actions, expectedActions(GridSignal.Connected, newState))
  }

  test(
    "requestConnection with multiple actors keeps all actors and stays Connected"
  ) {
    val state = State()
      .modify(_.grid.devicesRequestedConnection)
      .setTo(Set(GridSignal.Car))
    val (newState, actions) =
      manager.requestConnection(GridSignal.Batteries, state)

    assertEquals(
      newState.grid.devicesRequestedConnection,
      Set[GridSignal.ActorsConnecting](GridSignal.Car, GridSignal.Batteries)
    )
    assertEquals(newState.grid.lastCommandSent, Some(GridSignal.Connected))
    assertEquals(actions, expectedActions(GridSignal.Connected, newState))
  }

  test(
    "releaseConnection removes actor; sends Disconnected when no actors remain"
  ) {
    val state = State()
      .modify(_.grid.devicesRequestedConnection)
      .setTo(Set(GridSignal.Manual))
    val (newState, actions) =
      manager.releaseConnection(GridSignal.Manual, state)

    assertEquals(
      newState.grid.devicesRequestedConnection,
      Set.empty[GridSignal.ActorsConnecting]
    )
    assertEquals(newState.grid.lastCommandSent, Some(GridSignal.Disconnected))
    assertEquals(actions, expectedActions(GridSignal.Disconnected, newState))
  }

  test(
    "releaseConnection removes only the given actor; stays Connected when others remain"
  ) {
    val state = State()
      .modify(_.grid.devicesRequestedConnection)
      .setTo(Set(GridSignal.Manual, GridSignal.Car))
    val (newState, actions) =
      manager.releaseConnection(GridSignal.Manual, state)

    assertEquals(
      newState.grid.devicesRequestedConnection,
      Set[GridSignal.ActorsConnecting](GridSignal.Car)
    )
    assertEquals(newState.grid.lastCommandSent, Some(GridSignal.Connected))
    assertEquals(actions, expectedActions(GridSignal.Connected, newState))
  }

  test(
    "releaseConnection on absent actor leaves state unchanged and sends Disconnected"
  ) {
    val state = State()
    val (newState, actions) = manager.releaseConnection(GridSignal.Car, state)

    assertEquals(
      newState.grid.devicesRequestedConnection,
      Set.empty[GridSignal.ActorsConnecting]
    )
    assertEquals(newState.grid.lastCommandSent, Some(GridSignal.Disconnected))
    assertEquals(actions, expectedActions(GridSignal.Disconnected, newState))
  }

  test("applyConnection sends Connected when actors are present") {
    val state = State()
      .modify(_.grid.devicesRequestedConnection)
      .setTo(Set(GridSignal.Manual))
      .modify(_.grid.lastCommandSent)
      .setTo(Some(GridSignal.Disconnected))
    val expectedState =
      state.modify(_.grid.lastCommandSent).setTo(Some(GridSignal.Connected))
    val (newState, actions) = manager.applyConnection(state)

    assertEquals(newState, expectedState)
    assertEquals(actions, expectedActions(GridSignal.Connected, newState))
  }

  test("applyConnection sends Disconnected when no actors present") {
    val state = State()
    val expectedState =
      state.modify(_.grid.lastCommandSent).setTo(Some(GridSignal.Disconnected))
    val (newState, actions) = manager.applyConnection(state)

    assertEquals(newState, expectedState)
    assertEquals(actions, expectedActions(GridSignal.Disconnected, newState))
  }
}
