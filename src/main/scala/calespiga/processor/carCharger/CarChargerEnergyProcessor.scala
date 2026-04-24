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

        case Event.CarCharger.CarChargerAccumulatedEnergyReported(totalWh) =>
          val prevLast = state.carCharger.lastEnergyUpdate

          val isNewDay = prevLast.forall(prev =>
            prev.atZone(zone).toLocalDate != timestamp.atZone(zone).toLocalDate
          )

          val updatedAccumulatedAtDayStart: Float =
            if (isNewDay)
              state.carCharger.lastAccumulatedEnergyWh.getOrElse(totalWh)
            else state.carCharger.accumulatedAtDayStartWh.getOrElse(totalWh)

          val energySinceDayStart = totalWh - updatedAccumulatedAtDayStart

          val newState = state
            .modify(_.carCharger.lastEnergyUpdate)
            .setTo(Some(timestamp))
            .modify(_.carCharger.lastAccumulatedEnergyWh)
            .setTo(Some(totalWh))
            .modify(_.carCharger.accumulatedAtDayStartWh)
            .setTo(Some(updatedAccumulatedAtDayStart))

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.energyTodayItem,
              f"$energySinceDayStart%.1f"
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
