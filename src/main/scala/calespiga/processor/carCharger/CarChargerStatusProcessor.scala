package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import calespiga.model.CarChargerChargingStatus
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[carCharger] object CarChargerStatusProcessor {

  val IDLE_POWER: Float = 1.0f
  val BLOCKED_POWER: Float = 5.0f

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
          def statusFromPower(p: Float): CarChargerChargingStatus =
            if (p <= 0.0f) CarChargerChargingStatus.Disabled
            else if (p <= IDLE_POWER) CarChargerChargingStatus.Connected
            else if (p <= BLOCKED_POWER) CarChargerChargingStatus.Blocked
            else CarChargerChargingStatus.Charging

          val newChargingStatus = statusFromPower(watts)

          // previous power reading (before updating with this event)
          val previousPowerOpt = state.carCharger.currentPowerWatts
          val previousStatusOpt = previousPowerOpt.map(statusFromPower)

          // debounce: only update chargingStatus if previous power also falls
          // into the same status bucket
          val shouldSetChargingStatus =
            previousStatusOpt.contains(newChargingStatus)

          val stateWithPower =
            state.modify(_.carCharger.currentPowerWatts).setTo(Some(watts))

          val newState =
            if (shouldSetChargingStatus)
              stateWithPower
                .modify(_.carCharger.chargingStatus)
                .setTo(Some(newChargingStatus))
            else stateWithPower

          val baseActions = Set[Action](
            Action.SetUIItemValue(config.powerItem, watts.toInt.toString)
          )
          val statusAction =
            if (shouldSetChargingStatus)
              Set(
                Action.SetUIItemValue(
                  config.chargingStatusItem,
                  CarChargerChargingStatus.chargingStatusToString(
                    newChargingStatus
                  )
                )
              )
            else Set.empty[Action]

          (newState, baseActions ++ statusAction)

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(config: CarChargerConfig): SingleProcessor = Impl(config)
}
