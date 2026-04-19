package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import calespiga.processor.SingleProcessor

private[carCharger] object CarChargerEnergyProcessor {

  private final case class Impl(
      config: CarChargerConfig,
      zone: ZoneId
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.CarCharger.CarChargerPowerReported(watts) =>
          val newState =
            state.modify(_.carCharger.currentPowerWatts).setTo(Some(watts))

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.powerItem,
              watts.toInt.toString
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
  ): SingleProcessor = Impl(config, zone)
}
