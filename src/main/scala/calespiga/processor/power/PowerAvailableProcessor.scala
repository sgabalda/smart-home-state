package calespiga.processor.power

import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import calespiga.model.Event.Power.PowerProductionReported
import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.config.PowerAvailableProcessorConfig
import java.time.ZoneId
import calespiga.model.Event.System.StartupEvent
import calespiga.model.Event.Power.PowerProductionReadingError

object PowerAvailableProcessor {

  val ALERT_READING_ERROR = "power-available-reading-error"
  val ALERT_NO_PRODUCTION_IN_HOURS_ID = "power-available-no-production-hours"

  val STATUS_OK = "OK"
  val STATUS_TEMPORARY_ERROR = "Error temporal..."
  val STATUS_CONTINUOUS_ERROR = "Error continuat"

  private final case class Impl(
      config: PowerAvailableProcessorConfig,
      zoneId: ZoneId
  ) extends SingleProcessor {

    def actionsBasedOnPower(
        powerAvailable: Float
    ): Set[Action] = {
      Set(
        Option.when(powerAvailable > 0) {
          Action.Delayed(
            ALERT_NO_PRODUCTION_IN_HOURS_ID,
            Action.SendNotification(
              ALERT_NO_PRODUCTION_IN_HOURS_ID,
              s"No s'ha rebut cap potència generada en ${config.periodAlarmNoProduction}",
              None
            ),
            config.periodAlarmNoProduction
          )
        }
      ).flatten
    }

    def updateUIItemActions(
        powerAvailable: Float,
        powerProduced: Float,
        powerDiscarded: Float
    ): Set[Action] = {
      Set(
        Action.SetUIItemValue(
          config.powerAvailableItem,
          f"$powerAvailable%.0f"
        ),
        Action.SetUIItemValue(
          config.powerProducedItem,
          f"$powerProduced%.0f"
        ),
        Action.SetUIItemValue(
          config.powerDiscardedItem,
          f"$powerDiscarded%.0f"
        )
      )
    }
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match

      case StartupEvent =>
        (
          state
            .modify(_.powerProduction.powerAvailable)
            .setTo(None)
            .modify(_.powerProduction.powerProduced)
            .setTo(None)
            .modify(_.powerProduction.powerDiscarded)
            .setTo(None)
            .modify(_.powerProduction.lastError)
            .setTo(None),
          updateUIItemActions(0f, 0f, 0f) +
            Action.SetUIItemValue(
              config.readingsStatusItem,
              STATUS_OK
            )
        )
      case PowerProductionReadingError =>
        state.powerProduction.lastError match
          case Some(errorHappened)
              if (timestamp
                .minusMillis(config.periodAlarmWithError.toMillis)
                .isAfter(errorHappened)) =>
            // Send a new notification
            (
              state,
              Set(
                Action.SendNotification(
                  ALERT_READING_ERROR,
                  s"Hi ha error en la lectura de la potència produïda",
                  None
                ),
                Action.SetUIItemValue(
                  config.readingsStatusItem,
                  STATUS_CONTINUOUS_ERROR
                )
              )
            )
          case Some(value) =>
            // error already recorded, but not enough time to send a new notification
            (state, Set.empty)
          case None =>
            val newState = state
              .modify(_.powerProduction.powerAvailable)
              .setTo(None)
              .modify(_.powerProduction.powerProduced)
              .setTo(None)
              .modify(_.powerProduction.powerDiscarded)
              .setTo(None)
              .modify(_.powerProduction.lastError)
              .setTo(Some(timestamp))
            (
              newState,
              updateUIItemActions(0f, 0f, 0f)
                + Action.SetUIItemValue(
                  config.readingsStatusItem,
                  STATUS_TEMPORARY_ERROR
                )
            )
      case PowerProductionReported(
            powerAvailable,
            powerProduced,
            powerDiscarded,
            linesPower
          ) =>
        val newState = state
          .modify(_.powerProduction.powerAvailable)
          .setTo(Some(powerAvailable))
          .modify(_.powerProduction.powerProduced)
          .setTo(Some(powerProduced))
          .modify(_.powerProduction.powerDiscarded)
          .setTo(Some(powerDiscarded))
          .modify(_.powerProduction.linesPower)
          .setTo(linesPower)
          .modify(_.powerProduction.lastError)
          .setTo(None)
          .modify(_.powerProduction.lastUpdate)
          .setTo(Some(timestamp))
          .modify(_.powerProduction.lastProducedPower)
          .using(current =>
            if (powerProduced > 0)
              Some(timestamp)
            else
              current
          )
        (
          newState,
          updateUIItemActions(powerAvailable, powerProduced, powerDiscarded)
            ++ actionsBasedOnPower(powerAvailable) +
            Action.SetUIItemValue(
              config.readingsStatusItem,
              STATUS_OK
            )
        )
      case _ =>
        (state, Set.empty)
  }

  def apply(
      config: PowerAvailableProcessorConfig,
      zoneId: ZoneId
  ): SingleProcessor =
    Impl(config, zoneId)

}
