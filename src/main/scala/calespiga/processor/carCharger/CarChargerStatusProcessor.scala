package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[carCharger] object CarChargerStatusProcessor {

  private final case class Impl(
      config: CarChargerConfig
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.CarCharger.CarChargerStatusReported(status) =>
          val newState = state
            .modify(_.carCharger.switchStatus)
            .setTo(Some(status))

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.statusItem,
              calespiga.model.CarChargerSignal.controllerStateToString(status)
            )
          )

          (newState, actions)

        case Event.CarCharger.CarChargerPowerReported(watts) =>
          val newState = state
            .modify(_.carCharger.currentPowerWatts)
            .setTo(Some(watts))

          val actions = Set[Action](
            Action.SetUIItemValue(
              config.powerItem,
              watts.toInt.toString
            )
          )

          (newState, actions)

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(config: CarChargerConfig): SingleProcessor = Impl(config)
}
