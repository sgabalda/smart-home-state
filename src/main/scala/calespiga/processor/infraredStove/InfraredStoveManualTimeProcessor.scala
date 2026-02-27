package calespiga.processor.infraredStove

import calespiga.model.{State, Action, Event}
import java.time.Instant
import java.time.ZoneId
import calespiga.model.Event.InfraredStove.InfraredStovePowerCommandChanged
import calespiga.model.InfraredStoveSignal
import com.softwaremill.quicklens.*
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.ProcessorFormatter
import scala.concurrent.duration._
import java.time.{Duration => JavaDuration}

private object InfraredStoveManualTimeProcessor {

  val DELAY_ID = "infrared-stove-manual-time-processor"

  private final case class Impl(programmedOffTimeItem: String, zone: ZoneId)
      extends SingleProcessor {

    private def actionsToStopStoveWithDelay(
        delay: FiniteDuration
    ): Set[Action] =
      Set(
        Action.Delayed(
          id = DELAY_ID,
          action = Action.SendFeedbackEvent(
            Event.InfraredStove.InfraredStoveManualTimeExpired
          ),
          delay = delay
        )
      )

    private def actionsToStopStove(
        initialDelayMinutes: Option[Int]
    ): Set[Action] =
      initialDelayMinutes.fold(Set.empty[Action])(minutes =>
        actionsToStopStoveWithDelay(minutes.minutes)
      )

    private def offTimeString(
        setAt: Instant,
        maxMinutes: Int
    ): String =
      ProcessorFormatter.format(
        setAt.plusSeconds(maxMinutes.minutes.toSeconds),
        zone
      )

    private def setOffTimeAction(
        timestamp: Instant,
        manualMaxTimeMinutes: Option[Int]
    ): Action =
      Action.SetUIItemValue(
        programmedOffTimeItem,
        manualMaxTimeMinutes
          .map(minutes => offTimeString(timestamp, minutes))
          .getOrElse("")
      )

    private def clearOffTimeAction: Action =
      Action.SetUIItemValue(programmedOffTimeItem, "")

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case InfraredStovePowerCommandChanged(command) =>
        if Actions.isManualCommand(command) then
          val newState = state
            .modify(_.infraredStove.lastSetManual)
            .setTo(Some(timestamp))
          val actions =
            actionsToStopStove(state.infraredStove.manualMaxTimeMinutes) +
              setOffTimeAction(
                timestamp,
                state.infraredStove.manualMaxTimeMinutes
              )
          (newState, actions)
        else
          val newState = state
            .modify(_.infraredStove.lastSetManual)
            .setTo(None)
          val actions = Set(Action.Cancel(DELAY_ID), clearOffTimeAction)
          (newState, actions)

      case Event.System.StartupEvent =>
        (
          state.infraredStove.lastSetManual,
          state.infraredStove.manualMaxTimeMinutes,
          state.infraredStove.lastCommandReceived
        ) match
          case (Some(setAt), Some(maxMinutes), Some(lastCommand))
              if Actions.isManualCommand(lastCommand) =>
            val elapsedSeconds =
              JavaDuration.between(setAt, timestamp).toSeconds
            val remainingSeconds = maxMinutes.minutes.toSeconds - elapsedSeconds
            if remainingSeconds > 0 then
              (
                state,
                actionsToStopStoveWithDelay(remainingSeconds.seconds) +
                  Action.SetUIItemValue(
                    programmedOffTimeItem,
                    offTimeString(setAt, maxMinutes)
                  )
              )
            else
              (
                state,
                Set(
                  Action.SendFeedbackEvent(
                    Event.InfraredStove.InfraredStoveManualTimeExpired
                  ),
                  clearOffTimeAction
                )
              )
          case (Some(_), _, Some(lastCommand))
              if !Actions.isManualCommand(lastCommand) =>
            // if there is a lastSetManual but the last command is not manual, reset lastSetManual to None
            (
              state.modify(_.infraredStove.lastSetManual).setTo(None),
              Set(clearOffTimeAction)
            )
          case _ => (state, Set.empty[Action])

      case Event.InfraredStove.InfraredStoveManualTimeChanged(newMinutes) =>
        val newState = state
          .modify(_.infraredStove.manualMaxTimeMinutes)
          .setTo(Some(newMinutes))

        val actions = state.infraredStove.lastSetManual match
          case Some(setAt)
              if state.infraredStove.lastCommandReceived.exists(
                Actions.isManualCommand
              ) && !state.infraredStove.lastCommandSent.exists(
                _ == InfraredStoveSignal.Off
              ) =>
            val elapsedSeconds =
              JavaDuration.between(setAt, timestamp).toSeconds
            val remainingSeconds =
              Math.max(newMinutes.minutes.toSeconds - elapsedSeconds, 0)

            actionsToStopStoveWithDelay(remainingSeconds.seconds) +
              Action.SetUIItemValue(
                programmedOffTimeItem,
                offTimeString(setAt, newMinutes)
              )
          case _ => Set.empty[Action]

        (newState, actions)

      case _ =>
        (state, Set.empty)
    }

  }

  def apply(programmedOffTimeItem: String, zone: ZoneId): SingleProcessor =
    Impl(programmedOffTimeItem, zone)

}
