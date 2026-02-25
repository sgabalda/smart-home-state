package calespiga.processor.infraredStove

import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.model.Event.InfraredStove.InfraredStovePowerCommandChanged
import calespiga.model.InfraredStoveSignal
import com.softwaremill.quicklens.*
import calespiga.processor.SingleProcessor
import calespiga.config.InfraredStoveConfig
import scala.concurrent.duration._
import java.time.{Duration => JavaDuration}

private object InfraredStoveManualTimeProcessor {

  val DELAY_ID = "infrared-stove-manual-time-processor"

  private final case class Impl(
      config: InfraredStoveConfig
  ) extends SingleProcessor {

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

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case InfraredStovePowerCommandChanged(command) =>

        val prevCommand = state.infraredStove.lastCommandReceived
        val (lastSetManual, actions) = (prevCommand, command) match
          // if previous command was not manual and new command is manual, set lastSetManual to now
          case (Some(prev), newCmd)
              if !Actions
                .isManualCommand(prev) && Actions.isManualCommand(newCmd) =>
            (
              Some(timestamp),
              actionsToStopStove(state.infraredStove.manualMaxTimeMinutes)
            )
          // if new command is manual and no previous command, also set lastSetManual to now
          case (None, newCmd) if Actions.isManualCommand(newCmd) =>
            (
              Some(timestamp),
              actionsToStopStove(state.infraredStove.manualMaxTimeMinutes)
            )
          // if new command is not manual, reset lastSetManual to None and cancel any off command scheduled
          case (_, newCmd) if !Actions.isManualCommand(newCmd) =>
            (None, Set(Action.Cancel(DELAY_ID)))
          // otherwise, keep lastSetManual unchanged
          case _ => (state.infraredStove.lastSetManual, Set.empty[Action])

        val newState = state
          .modify(_.infraredStove.lastSetManual)
          .setTo(lastSetManual)

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
              (state, actionsToStopStoveWithDelay(remainingSeconds.seconds))
            else
              (
                state,
                Set(
                  Action.SendFeedbackEvent(
                    Event.InfraredStove.InfraredStoveManualTimeExpired
                  )
                )
              )
          case (Some(setAt), _, Some(lastCommand))
              if !Actions.isManualCommand(lastCommand) =>
            // if there is a lastSetManual but the last command is not manual, reset lastSetManual to None
            (
              state.modify(_.infraredStove.lastSetManual).setTo(None),
              Set.empty[Action]
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
              ) =>
            val elapsedSeconds =
              JavaDuration.between(setAt, timestamp).toSeconds
            val remainingSeconds = newMinutes.minutes.toSeconds - elapsedSeconds
            if remainingSeconds > 0 then
              actionsToStopStoveWithDelay(remainingSeconds.seconds)
            else Set(Action.Cancel(DELAY_ID))
          case _ => Set.empty[Action]

        (newState, actions)

      case _ =>
        (state, Set.empty)
    }

  }

  def apply(
      config: InfraredStoveConfig
  ): SingleProcessor = Impl(config)

}
