package calespiga.processor.battery

import calespiga.config.BatteryConfig
import calespiga.model.{Action, Event, GridTariff, State}
import calespiga.model.{BatteryChargeTariff, BatteryStatus}
import calespiga.model.GridSignal
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[battery] object BatteryChargeProcessor {

  private def shouldChargeBattery(state: State): Boolean =
    state.battery.status match
      case Some(BatteryStatus.Low) =>
        state.battery.lowChargeTariff.exists {
          case BatteryChargeTariff.AllTariffs =>
            state.grid.currentTariff.isDefined
          case BatteryChargeTariff.PlaAndVall =>
            state.grid.currentTariff.exists(t =>
              t == GridTariff.Pla || t == GridTariff.Vall
            )
          case BatteryChargeTariff.Vall =>
            state.grid.currentTariff.contains(GridTariff.Vall)
          case BatteryChargeTariff.NoneCharge => false
        }
      case Some(BatteryStatus.Medium) =>
        state.battery.mediumChargeTariff.exists {
          case BatteryChargeTariff.AllTariffs =>
            state.grid.currentTariff.isDefined
          case BatteryChargeTariff.PlaAndVall =>
            state.grid.currentTariff.exists(t =>
              t == GridTariff.Pla || t == GridTariff.Vall
            )
          case BatteryChargeTariff.Vall =>
            state.grid.currentTariff.contains(GridTariff.Vall)
          case BatteryChargeTariff.NoneCharge => false
        }
      case Some(BatteryStatus.High) => false
      case None                     => false

  private final case class Impl(
      config: BatteryConfig,
      manager: calespiga.processor.grid.GridConnectionManager
  ) extends SingleProcessor {

    private def applyConnectionChange(
        state: State,
        shouldConnect: Boolean
    ): (State, Set[Action]) =
      if shouldConnect then
        manager.requestConnection(GridSignal.Batteries, state)
      else manager.releaseConnection(GridSignal.Batteries, state)
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case Event.Battery.BatteryStatusReported(status) =>
        val newState = state.modify(_.battery.status).setTo(Some(status))
        val actions = Set(
          Action.SetUIItemValue(config.statusItem, status.label)
        )
        val shouldConnect = shouldChargeBattery(newState)
        val (stateAfter, managerActions) =
          applyConnectionChange(newState, shouldConnect)
        (stateAfter, actions ++ managerActions)

      case Event.Battery.BatteryChargeLowTariffChanged(tariff) =>
        val newState =
          state.modify(_.battery.lowChargeTariff).setTo(Some(tariff))
        val actions = Set(
          Action.SetUIItemValue(config.lowChargeTariffItem, tariff.label)
        )
        val shouldConnect = shouldChargeBattery(newState)
        val (stateAfter, managerActions) =
          applyConnectionChange(newState, shouldConnect)
        (stateAfter, actions ++ managerActions)

      case Event.Battery.BatteryChargeMediumTariffChanged(tariff) =>
        val newState =
          state.modify(_.battery.mediumChargeTariff).setTo(Some(tariff))
        val actions = Set(
          Action.SetUIItemValue(config.mediumChargeTariffItem, tariff.label)
        )
        val shouldConnect = shouldChargeBattery(newState)
        val (stateAfter, managerActions) =
          applyConnectionChange(newState, shouldConnect)
        (stateAfter, actions ++ managerActions)

      case Event.Grid.GridTariffChanged(_) =>
        val shouldConnect = shouldChargeBattery(state)
        val (stateAfter, managerActions) =
          applyConnectionChange(state, shouldConnect)
        (stateAfter, managerActions)

      case Event.System.StartupEvent =>
        val actionsBuilder = Set.newBuilder[Action]
        state.battery.status.foreach { status =>
          actionsBuilder += Action.SetUIItemValue(
            config.statusItem,
            status.label
          )
        }
        state.battery.lowChargeTariff.foreach { tariff =>
          actionsBuilder += Action.SetUIItemValue(
            config.lowChargeTariffItem,
            tariff.label
          )
        }
        state.battery.mediumChargeTariff.foreach { tariff =>
          actionsBuilder += Action.SetUIItemValue(
            config.mediumChargeTariffItem,
            tariff.label
          )
        }
        (state, actionsBuilder.result())

      case _ =>
        (state, Set.empty)
    }
  }

  def apply(
      config: BatteryConfig,
      manager: calespiga.processor.grid.GridConnectionManager
  ): SingleProcessor = Impl(config, manager)
}
