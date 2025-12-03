package calespiga.processor.power

import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import calespiga.model.Event.Power.PowerProductionReported
import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.config.PowerAvailableProcessorConfig

object PowerAvailableProcessor {

  val ALERT_NO_UPDATES_ID = "power-available-no-updates"
  val ALERT_NO_PRODUCTION_IN_HOURS_ID = "power-available-no-production-hours"

  private final case class Impl(config: PowerAvailableProcessorConfig)
      extends SingleProcessor {

    def actionsBasedOnPower(
        powerAvailable: Float
    ): Set[Action] = {
      Set(
        Some(
          Action.Delayed(
            ALERT_NO_UPDATES_ID,
            Action.SendNotification(
              ALERT_NO_UPDATES_ID,
              s"No s'han rebut dades de potència disponible en ${config.periodAlarmNoData}",
              None
            ),
            config.periodAlarmNoData
          )
        ),
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
            ++ actionsBasedOnPower(powerAvailable)
        )
      case _ =>
        (state, Set.empty)
  }

  def apply(config: PowerAvailableProcessorConfig): SingleProcessor =
    Impl(config)

}
