package calespiga.processor.battery

import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import java.time.Instant
import calespiga.model.BatteryStatus

private[battery] object BatteryAlertOnLowProcessor {

  val NOTIFICATION_ID = "battery_low_alert"
  val NOTIFICATION_MESSAGE =
    "La bateria és baixa. Comproveu la connexió a la xarxa o configureu-la correctament"

  private final case class Impl() extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.Battery.BatteryStatusReported(status)
            if status == BatteryStatus.Low =>
          (
            state,
            Set(
              Action.SendNotification(
                NOTIFICATION_ID,
                NOTIFICATION_MESSAGE,
                None
              )
            )
          )

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(): SingleProcessor =
    Impl()
}
