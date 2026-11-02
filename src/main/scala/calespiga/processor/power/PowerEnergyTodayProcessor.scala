package calespiga.processor.power

import calespiga.config.PowerEnergyTodayConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.GridTariff
import calespiga.model.State
import calespiga.model.State.PowerEnergyToday
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId

private[power] object PowerEnergyTodayProcessor {

  private final case class Impl(
      config: PowerEnergyTodayConfig,
      zone: ZoneId
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case Event.System.StartupEvent =>
        val (energy, _) = forToday(state.powerManagement.energyToday, timestamp)
        val tariff = state.grid.currentTariff.orElse(energy.lastGridTariff)
        val newState =
          state
            .modify(_.powerManagement.energyToday)
            .setTo(energy.modify(_.lastGridTariff).setTo(tariff))
        (newState, energyUIActions(energy, updateAll = true))

      case Event.Grid.GridTariffChanged(tariff) =>
        val previous = state.powerManagement.energyToday
        val (today, dayChanged) = forToday(previous, timestamp)
        // GridProcessor has already updated currentTariff for this event.
        val updated = today.lastGridPowerUpdate
          .zip(today.lastGridPower)
          .foldLeft(today) { case (energy, (lastUpdate, lastPower)) =>
            if (timestamp.isAfter(lastUpdate)) {
              val intervalTariff =
                today.lastGridTariff
                  .orElse(state.grid.currentTariff)
                  .getOrElse(tariff)
              addGridEnergy(
                energy,
                lastUpdate,
                timestamp,
                lastPower,
                intervalTariff
              )
                .modify(_.lastGridPowerUpdate)
                .setTo(Some(timestamp))
            } else energy
          }
          .modify(_.lastGridTariff)
          .setTo(Some(tariff))
        val newState =
          state.modify(_.powerManagement.energyToday).setTo(updated)
        (
          newState,
          energyUIActions(updated, updateAll = dayChanged, updateGrid = true)
        )

      case Event.Power.HomeConsumptionEnergyReported(energyWh)
          if energyWh.isFinite =>
        val previous = state.powerManagement.energyToday
        val (today, dayChanged) = forToday(previous, timestamp)
        val isNewerReading = today.lastHomeMeterUpdate.forall(
          previousTimestamp => timestamp.isAfter(previousTimestamp)
        )

        if (!isNewerReading) (state, Set.empty)
        else {
          val energySinceLastReading =
            for {
              previousTimestamp <- today.lastHomeMeterUpdate
              previousEnergy <- today.lastHomeMeterEnergyWh
              if previousTimestamp.atZone(zone).toLocalDate ==
                timestamp.atZone(zone).toLocalDate
              if energyWh >= previousEnergy
            } yield energyWh - previousEnergy

          val updated = today
            .modify(_.homeConsumptionWh)
            .using(_ + energySinceLastReading.getOrElse(0f))
            .modify(_.lastHomeMeterEnergyWh)
            .setTo(Some(energyWh))
            .modify(_.lastHomeMeterUpdate)
            .setTo(Some(timestamp))
          val newState =
            state.modify(_.powerManagement.energyToday).setTo(updated)
          (
            newState,
            energyUIActions(
              updated,
              updateAll = dayChanged,
              updateHome = true
            )
          )
        }

      case Event.Power.PowerStatusReported(_, _, powerFromNetwork)
          if powerFromNetwork.isFinite =>
        val previous = state.powerManagement.energyToday
        val (today, dayChanged) = forToday(previous, timestamp)
        val isNewerReading = today.lastGridPowerUpdate.forall(
          previousTimestamp => timestamp.isAfter(previousTimestamp)
        )

        if (!isNewerReading) (state, Set.empty)
        else {
          val intervalTariff =
            today.lastGridTariff.orElse(state.grid.currentTariff)
          val updated = today.lastGridPowerUpdate
            .zip(today.lastGridPower)
            .zip(intervalTariff)
            .foldLeft(today) {
              case (energy, ((lastUpdate, lastPower), tariff)) =>
                if (timestamp.isAfter(lastUpdate))
                  addGridEnergy(
                    energy,
                    lastUpdate,
                    timestamp,
                    lastPower,
                    tariff
                  )
                else energy
            }
            .modify(_.lastGridPower)
            .setTo(Some(powerFromNetwork))
            .modify(_.lastGridPowerUpdate)
            .setTo(Some(timestamp))
            .modify(_.lastGridTariff)
            .setTo(state.grid.currentTariff.orElse(today.lastGridTariff))
          val newState =
            state.modify(_.powerManagement.energyToday).setTo(updated)
          (
            newState,
            energyUIActions(
              updated,
              updateAll = dayChanged,
              updateGrid = true
            )
          )
        }

      case _ => (state, Set.empty)
    }

    private def forToday(
        energy: PowerEnergyToday,
        timestamp: Instant
    ): (PowerEnergyToday, Boolean) = {
      val today = timestamp.atZone(zone).toLocalDate.toString
      if (energy.day.contains(today)) (energy, false)
      else {
        val reset = energy
          .modify(_.day)
          .setTo(Some(today))
          .modify(_.homeConsumptionWh)
          .setTo(0f)
          .modify(_.gridConsumedWh)
          .setTo(0f)
          .modify(_.gridInjectedWh)
          .setTo(0f)
          .modify(_.gridConsumedVallWh)
          .setTo(0f)
          .modify(_.gridConsumedPlaWh)
          .setTo(0f)
          .modify(_.gridConsumedPicWh)
          .setTo(0f)
          .modify(_.gridInjectedVallWh)
          .setTo(0f)
          .modify(_.gridInjectedPlaWh)
          .setTo(0f)
          .modify(_.gridInjectedPicWh)
          .setTo(0f)
        (reset, true)
      }
    }

    private def addGridEnergy(
        energy: PowerEnergyToday,
        lastUpdate: Instant,
        currentTimestamp: Instant,
        previousPower: Float,
        tariff: GridTariff
    ): PowerEnergyToday = {
      val dayStart =
        currentTimestamp.atZone(zone).toLocalDate.atStartOfDay(zone).toInstant
      val intervalStart =
        if (lastUpdate.isBefore(dayStart)) dayStart else lastUpdate
      val wattHours =
        java.time.Duration
          .between(intervalStart, currentTimestamp)
          .toNanos
          .toDouble * previousPower / 3_600_000_000_000d

      if (previousPower >= 0f)
        addConsumedEnergy(energy, tariff, wattHours.toFloat)
      else
        addInjectedEnergy(energy, tariff, -wattHours.toFloat)
    }

    private def addConsumedEnergy(
        energy: PowerEnergyToday,
        tariff: GridTariff,
        wattHours: Float
    ): PowerEnergyToday = {
      val withTotal =
        energy.modify(_.gridConsumedWh).using(_ + wattHours)
      tariff match {
        case GridTariff.Vall =>
          withTotal.modify(_.gridConsumedVallWh).using(_ + wattHours)
        case GridTariff.Pla =>
          withTotal.modify(_.gridConsumedPlaWh).using(_ + wattHours)
        case GridTariff.Pic =>
          withTotal.modify(_.gridConsumedPicWh).using(_ + wattHours)
      }
    }

    private def addInjectedEnergy(
        energy: PowerEnergyToday,
        tariff: GridTariff,
        wattHours: Float
    ): PowerEnergyToday = {
      val withTotal =
        energy.modify(_.gridInjectedWh).using(_ + wattHours)
      tariff match {
        case GridTariff.Vall =>
          withTotal.modify(_.gridInjectedVallWh).using(_ + wattHours)
        case GridTariff.Pla =>
          withTotal.modify(_.gridInjectedPlaWh).using(_ + wattHours)
        case GridTariff.Pic =>
          withTotal.modify(_.gridInjectedPicWh).using(_ + wattHours)
      }
    }

    private def energyUIActions(
        energy: PowerEnergyToday,
        updateAll: Boolean,
        updateHome: Boolean = false,
        updateGrid: Boolean = false
    ): Set[Action] = {
      val homeActions =
        Option.when(updateAll || updateHome)(
          Action.SetUIItemValue(
            config.homeConsumptionItem,
            f"${energy.homeConsumptionWh / 1000f}%.3f"
          )
        )
      val gridActions =
        if (updateAll || updateGrid)
          Set(
            Action.SetUIItemValue(
              config.gridConsumedItem,
              f"${energy.gridConsumedWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridInjectedItem,
              f"${energy.gridInjectedWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridConsumedVallItem,
              f"${energy.gridConsumedVallWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridConsumedPlaItem,
              f"${energy.gridConsumedPlaWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridConsumedPicItem,
              f"${energy.gridConsumedPicWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridInjectedVallItem,
              f"${energy.gridInjectedVallWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridInjectedPlaItem,
              f"${energy.gridInjectedPlaWh / 1000f}%.3f"
            ),
            Action.SetUIItemValue(
              config.gridInjectedPicItem,
              f"${energy.gridInjectedPicWh / 1000f}%.3f"
            )
          )
        else Set.empty

      homeActions.toSet ++ gridActions
    }
  }

  def apply(
      config: PowerEnergyTodayConfig,
      zone: ZoneId
  ): SingleProcessor = Impl(config, zone)
}
