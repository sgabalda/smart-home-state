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
          val baseEnergyToday: Float = (
            for {
              lastAccum <- state.carCharger.lastAccumulatedEnergyWh
              dayStart <- state.carCharger.accumulatedAtDayStartWh
            } yield lastAccum - dayStart
          ).getOrElse(0f)

          val newEnergyToday = energyCalculator.calculateEnergyToday(
            state.carCharger.lastEnergyUpdate,
            timestamp,
            state.carCharger.currentPowerWatts.map(_.toInt).getOrElse(0),
            baseEnergyToday,
            zone
          )

          val newState =
            state.modify(_.carCharger.lastEnergyUpdate).setTo(Some(timestamp))

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.energyTodayItem,
              newEnergyToday.toInt.toString
            )
          )

          (newState, actions)

        case Event.CarCharger.CarChargerAccumulatedEnergyReported(totalWh) =>
          val prevLast = state.carCharger.lastEnergyUpdate

          val isNewDay = prevLast.exists(prev =>
            prev.atZone(zone).toLocalDate != timestamp.atZone(zone).toLocalDate
          )

          val updatedAccumulatedAtDayStart: Option[Float] =
            if (isNewDay)
              state.carCharger.lastAccumulatedEnergyWh.orElse(Some(totalWh))
            else state.carCharger.accumulatedAtDayStartWh.orElse(Some(totalWh))

          val energySinceDayStart =
            totalWh - updatedAccumulatedAtDayStart.getOrElse(totalWh)

          val newState = state
            .modify(_.carCharger.lastEnergyUpdate)
            .setTo(Some(timestamp))
            .modify(_.carCharger.lastAccumulatedEnergyWh)
            .setTo(Some(totalWh))
            .modify(_.carCharger.accumulatedAtDayStartWh)
            .setTo(updatedAccumulatedAtDayStart)

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.energyTodayItem,
              energySinceDayStart.toInt.toString
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
