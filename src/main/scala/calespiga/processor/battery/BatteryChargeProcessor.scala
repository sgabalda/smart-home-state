package calespiga.processor.battery

import calespiga.config.BatteryConfig
import calespiga.model.{Action, Event, GridTariff, State}
import calespiga.model.{BatteryChargeTariff, BatteryStatus}
import calespiga.model.GridSignal
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[battery] object BatteryChargeProcessor {

  private def matchesTariff(
      current: Option[GridTariff],
      config: BatteryChargeTariff
  ): Boolean =
    config match
      case BatteryChargeTariff.AllTariffs =>
        current.isDefined
      case BatteryChargeTariff.PlaAndVall =>
        current.exists(t => t == GridTariff.Pla || t == GridTariff.Vall)
      case BatteryChargeTariff.Vall =>
        current.contains(GridTariff.Vall)
      case BatteryChargeTariff.NoneCharge =>
        false

  private def shouldChargeBattery(state: State): Boolean =
    val currentTariff = state.grid.currentTariff

    state.battery.status match
      case Some(BatteryStatus.Low) =>
        state.battery.lowChargeTariff.exists(matchesTariff(currentTariff, _))

      case Some(BatteryStatus.Medium) =>
        state.battery.mediumChargeTariff.exists(matchesTariff(currentTariff, _))

      case Some(BatteryStatus.High) | None =>
        false

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

    private def updateStateAndReconnect(
        newState: State,
        actions: Set[Action]
    ): (State, Set[Action]) =
      val shouldConnect = shouldChargeBattery(newState)
      val (stateAfter, managerActions) =
        applyConnectionChange(newState, shouldConnect)
      (stateAfter, actions ++ managerActions)

    private def addIfDefined[A](
        opt: Option[A],
        item: String,
        toLabel: A => String,
        builder: scala.collection.mutable.Builder[Action, Set[Action]]
    ): Unit =
      opt.foreach(v => builder += Action.SetUIItemValue(item, toLabel(v)))

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.Battery.BatteryStatusReported(status) =>
          val newState =
            state.modify(_.battery.status).setTo(Some(status))
          val actions =
            Set[Action](Action.SetUIItemValue(config.statusItem, status.label))

          updateStateAndReconnect(newState, actions)

        case Event.Battery.BatteryChargeLowTariffChanged(tariff) =>
          val newState =
            state.modify(_.battery.lowChargeTariff).setTo(Some(tariff))

          updateStateAndReconnect(newState, Set.empty)

        case Event.Battery.BatteryChargeMediumTariffChanged(tariff) =>
          val newState =
            state.modify(_.battery.mediumChargeTariff).setTo(Some(tariff))

          updateStateAndReconnect(newState, Set.empty)

        case Event.Grid.GridTariffChanged(_) =>
          val shouldConnect = shouldChargeBattery(state)
          applyConnectionChange(state, shouldConnect)

        case Event.System.StartupEvent =>
          val actionsBuilder = Set.newBuilder[Action]

          addIfDefined(
            state.battery.status,
            config.statusItem,
            _.label,
            actionsBuilder
          )
          addIfDefined(
            state.battery.lowChargeTariff,
            config.lowChargeTariffItem,
            _.label,
            actionsBuilder
          )
          addIfDefined(
            state.battery.mediumChargeTariff,
            config.mediumChargeTariffItem,
            _.label,
            actionsBuilder
          )

          (state, actionsBuilder.result())

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(
      config: BatteryConfig,
      manager: calespiga.processor.grid.GridConnectionManager
  ): SingleProcessor =
    Impl(config, manager)
}
