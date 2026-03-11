package calespiga.processor.grid

import calespiga.model.Action
import calespiga.model.GridSignal
import calespiga.model.State
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*
import calespiga.config.GridConfig

/** Handles grid connection commands and status updates.
  *
  * The grid is connected when at least one device has requested a connection.
  * It disconnects only when no devices are requesting a connection.
  */
trait GridConnectionManager {
  def requestConnection(
      actor: GridSignal.ActorsConnecting,
      state: State
  ): (State, Set[Action])

  def releaseConnection(
      actor: GridSignal.ActorsConnecting,
      state: State
  ): (State, Set[Action])

  def applyConnection(state: State): (State, Set[Action])
}

object GridConnectionManager {

  private final case class Impl(
      gridActions: CommandActions[GridSignal.ControllerState]
  ) extends GridConnectionManager {

    private def shouldBeConnected(grid: State.Grid): Boolean =
      grid.devicesRequestedConnection.nonEmpty

    private def desiredCommand(grid: State.Grid): GridSignal.ControllerState =
      if (shouldBeConnected(grid)) GridSignal.Connected
      else GridSignal.Disconnected

    override def requestConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      val newState =
        state.modify(_.grid.devicesRequestedConnection).using(_ + actor)
      val cmd = desiredCommand(newState.grid)
      val stateWithCmd =
        newState.modify(_.grid.lastCommandSent).setTo(Some(cmd))
      (stateWithCmd, gridActions.commandActionWithResend(cmd))
    }

    override def releaseConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      val newState =
        state.modify(_.grid.devicesRequestedConnection).using(_ - actor)
      val cmd = desiredCommand(newState.grid)
      val stateWithCmd =
        newState.modify(_.grid.lastCommandSent).setTo(Some(cmd))
      (stateWithCmd, gridActions.commandActionWithResend(cmd))
    }
    def applyConnection(state: State): (State, Set[Action]) =
      (state, gridActions.commandActionWithResend(desiredCommand(state.grid)))
  }

  def apply(
      config: GridConfig
  ): GridConnectionManager =
    Impl(
      gridActions = Actions(config)
    )
}
