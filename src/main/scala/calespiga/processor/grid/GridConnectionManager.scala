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
      gridActions: CommandActions[GridSignal.ControllerState],
      config: GridConfig
  ) extends GridConnectionManager {

    private def shouldBeConnected(grid: State.Grid): Boolean =
      grid.devicesRequestedConnection.nonEmpty

    private def desiredCommand(grid: State.Grid): GridSignal.ControllerState =
      if (shouldBeConnected(grid)) GridSignal.Connected
      else GridSignal.Disconnected

    private def actorsUpdateActions(state: State): Set[Action] = Set(
      Action.SetUIItemValue(
        config.reasonItem,
        state.grid.devicesRequestedConnection
          .map(_.toString)
          .toSeq
          .sorted
          .mkString(",")
      )
    )

    override def requestConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      val newState =
        state.modify(_.grid.devicesRequestedConnection).using(_ + actor)
      val cmd = desiredCommand(newState.grid)
      val stateWithCmd =
        newState.modify(_.grid.lastCommandSent).setTo(Some(cmd))
      (
        stateWithCmd,
        gridActions.commandActionWithResend(cmd) ++ actorsUpdateActions(
          stateWithCmd
        )
      )
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
      (
        stateWithCmd,
        gridActions.commandActionWithResend(cmd) ++ actorsUpdateActions(
          stateWithCmd
        )
      )
    }
    def applyConnection(state: State): (State, Set[Action]) = {
      val cmd = desiredCommand(state.grid)
      (
        state.modify(_.grid.lastCommandSent).setTo(Some(cmd)),
        gridActions.commandActionWithResend(cmd) ++ actorsUpdateActions(state)
      )
    }

  }

  def apply(
      config: GridConfig
  ): GridConnectionManager =
    Impl(
      gridActions = Actions(config),
      config
    )
}
