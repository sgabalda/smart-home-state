package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.model.{Action, Event, State}
import calespiga.model.GridSignal
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private object GridConnectionProcessor {

  private final case class Impl(
      config: GridConfig,
      manager: GridConnectionManager
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case gd: Event.Grid.GridData =>
        gd match {
          case Event.Grid.GridConnectionStatusReported(status) =>
            val newState = state.modify(_.grid.status).setTo(Some(status))
            val actions: Set[Action] = Set(
              Action.SetUIItemValue(
                config.statusItem,
                GridSignal.controllerStateToUiString(status)
              )
            )
            (newState, actions)

          case Event.Grid.GridManualConnectionChanged(connect) =>
            if (connect)
              manager.requestConnection(GridSignal.Manual, state)
            else
              manager.releaseConnection(GridSignal.Manual, state)

          case _ => (state, Set.empty)
        }

      case Event.System.StartupEvent =>
        val (newState, acts) = manager.applyConnection(state)
        val actWithUi = acts + Action.SetUIItemValue(
          config.manualSwitchItem,
          newState.grid.devicesRequestedConnection
            .contains(GridSignal.Manual)
            .toString
        )
        (newState, actWithUi)

      case _ => (state, Set.empty)
    }
  }

  def apply(
      config: GridConfig,
      manager: GridConnectionManager
  ): SingleProcessor =
    Impl(config, manager)
}
