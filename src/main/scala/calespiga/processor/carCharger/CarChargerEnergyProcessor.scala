package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.EnergyCalculator

private[carCharger] object CarChargerEnergyProcessor {

  private final case class Impl(
      config: CarChargerConfig,
      zone: ZoneId,
      energyCalculator: EnergyCalculator
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.CarCharger.CarChargerPowerReported(watts) =>
          val newEnergyToday = energyCalculator.calculateEnergyToday(
            state.carCharger.lastPowerUpdate,
            timestamp,
            state.carCharger.currentPowerWatts.map(_.toInt).getOrElse(0),
            state.carCharger.energyTodayWh,
            zone
          )

          val newState = state
            .modify(_.carCharger.lastPowerUpdate)
            .setTo(Some(timestamp))
            .modify(_.carCharger.energyTodayWh)
            .setTo(newEnergyToday)

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.energyTodayItem,
              newEnergyToday.toInt.toString
            )
          )

          (newState, actions)

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(
      config: CarChargerConfig,
      zone: ZoneId
  ): SingleProcessor = Impl(config, zone, EnergyCalculator())

  private[carCharger] def apply(
      config: CarChargerConfig,
      zone: ZoneId,
      energyCalculator: EnergyCalculator
  ): SingleProcessor = Impl(config, zone, energyCalculator)
}
