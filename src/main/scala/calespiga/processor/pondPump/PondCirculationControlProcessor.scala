package calespiga.processor.pondPump

import calespiga.config.PondPumpConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.PondCirculation.*
import calespiga.model.PondCirculationCommand
import calespiga.model.PondCirculationCommand.Depuradora as DepuradoraCommand
import calespiga.model.PondCirculationCommand.Diposit as DipositCommand
import calespiga.model.PondCirculationCommand.Stop
import calespiga.model.PondCirculationMode
import calespiga.model.PondCirculationMode.Depuradora
import calespiga.model.PondCirculationMode.Diposit
import calespiga.model.PondCirculationStatus
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.*

private[pondPump] object PondCirculationControlProcessor {

  val SCHEDULE_ID = "pond-circulation-schedule"
  private val MINUTES_PER_DAY = 24 * 60

  private final case class Impl(
      config: PondPumpConfig,
      zone: ZoneId
  ) extends SingleProcessor {

    private val commandId = config.id + "-circulation"

    private val commands = CommandActions[PondCirculationCommand](
      mqttTopic = config.circulationMqttTopicForCommand,
      id = commandId,
      resendInterval = config.resendInterval,
      commandToMessage = PondCirculationCommand.toString
    )

    private def isWithinSchedule(state: State, timestamp: Instant): Boolean = {
      val hour = timestamp.atZone(zone).getHour
      val startHour = state.pondCirculation.startHour
      val endHour = state.pondCirculation.endHour

      if startHour <= endHour then hour >= startHour && hour <= endHour
      else hour >= startHour || hour <= endHour
    }

    private def desiredCommand(
        state: State,
        timestamp: Instant
    ): PondCirculationCommand =
      state.pondCirculation.selectedMode match
        case Diposit    => DipositCommand
        case Depuradora =>
          val localTime = timestamp.atZone(zone).toLocalTime
          val minutesIntoCycle = localTime.getMinute % 10
          val onMinutes = state.pondCirculation.cadencePercentage / 10
          if isWithinSchedule(state, timestamp) &&
            minutesIntoCycle < onMinutes
          then DepuradoraCommand
          else Stop

    private def reconcileCommand(
        state: State,
        timestamp: Instant,
        force: Boolean
    ): (State, Set[Action]) = {
      val command = desiredCommand(state, timestamp)
      if force || state.pondCirculation.lastCommandSent != Some(command) then
        (
          state
            .modify(_.pondCirculation.lastCommandSent)
            .setTo(Some(command)),
          commands.commandActionWithResend(command)
        )
      else (state, Set.empty)
    }

    private def nextTransition(
        state: State,
        timestamp: Instant
    ): Option[Instant] = {
      val currentCommand = desiredCommand(state, timestamp)
      var candidate =
        timestamp.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
      var remaining = MINUTES_PER_DAY * 3
      var transition: Option[Instant] = None

      while remaining > 0 && transition.isEmpty do
        if desiredCommand(state, candidate) != currentCommand then
          transition = Some(candidate)
        candidate = candidate.plus(1, ChronoUnit.MINUTES)
        remaining -= 1

      transition
    }

    private def scheduleAction(
        state: State,
        timestamp: Instant
    ): Set[Action] =
      if state.pondCirculation.selectedMode == Depuradora then
        nextTransition(state, timestamp).fold(
          Set[Action](Action.Cancel(SCHEDULE_ID))
        ) { transition =>
          Set(
            Action.Delayed(
              SCHEDULE_ID,
              Action.SendFeedbackEvent(ScheduleTransition),
              Duration.between(timestamp, transition).toNanos.nanos
            )
          )
        }
      else Set(Action.Cancel(SCHEDULE_ID))

    private def scheduleDescription(state: State): String =
      s"Encesa de ${state.pondCirculation.startHour} a ${state.pondCirculation.endHour}, ${state.pondCirculation.cadencePercentage}% del temps"

    private def displaySettings(state: State): Set[Action] =
      Set(
        Action.SetUIItemValue(
          config.circulationModeItem,
          PondCirculationMode.toString(state.pondCirculation.selectedMode)
        ),
        Action.SetUIItemValue(
          config.circulationStartHourItem,
          state.pondCirculation.startHour.toString
        ),
        Action.SetUIItemValue(
          config.circulationEndHourItem,
          state.pondCirculation.endHour.toString
        ),
        Action.SetUIItemValue(
          config.circulationCadenceItem,
          state.pondCirculation.cadencePercentage.toString
        ),
        Action.SetUIItemValue(
          config.circulationScheduleDescriptionItem,
          scheduleDescription(state)
        )
      ) ++ state.pondCirculation.status.map { status =>
        Action.SetUIItemValue(
          config.circulationStatusItem,
          PondCirculationStatus.toString(status)
        )
      }

    private def updateSettings(
        state: State,
        timestamp: Instant,
        force: Boolean
    ): (State, Set[Action]) = {
      val (newState, commandActions) =
        reconcileCommand(state, timestamp, force)
      (
        newState,
        displaySettings(newState) ++ commandActions ++
          scheduleAction(newState, timestamp)
      )
    }

    private def restoreItem(item: String, value: String): Set[Action] =
      Set(Action.SetUIItemValue(item, value))

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case ModeChanged(rawMode) =>
        PondCirculationMode.fromString(rawMode) match
          case Some(mode) =>
            val newState =
              state.modify(_.pondCirculation.selectedMode).setTo(mode)
            updateSettings(newState, timestamp, force = true)
          case _ =>
            (
              state,
              restoreItem(
                config.circulationModeItem,
                PondCirculationMode.toString(
                  state.pondCirculation.selectedMode
                )
              )
            )

      case StartHourChanged(hour)
          if hour >= 0 && hour <= 23 &&
            hour == state.pondCirculation.startHour =>
        (state, Set.empty)

      case StartHourChanged(hour) if hour >= 0 && hour <= 23 =>
        val newState = state.modify(_.pondCirculation.startHour).setTo(hour)
        updateSettings(newState, timestamp, force = false)

      case StartHourChanged(_) =>
        (
          state,
          restoreItem(
            config.circulationStartHourItem,
            state.pondCirculation.startHour.toString
          )
        )

      case EndHourChanged(hour)
          if hour >= 0 && hour <= 23 &&
            hour == state.pondCirculation.endHour =>
        (state, Set.empty)

      case EndHourChanged(hour) if hour >= 0 && hour <= 23 =>
        val newState = state.modify(_.pondCirculation.endHour).setTo(hour)
        updateSettings(newState, timestamp, force = false)

      case EndHourChanged(_) =>
        (
          state,
          restoreItem(
            config.circulationEndHourItem,
            state.pondCirculation.endHour.toString
          )
        )

      case CadenceChanged(percentage)
          if percentage >= 10 && percentage <= 100 &&
            percentage % 10 == 0 &&
            percentage == state.pondCirculation.cadencePercentage =>
        (state, Set.empty)

      case CadenceChanged(percentage)
          if percentage >= 10 && percentage <= 100 &&
            percentage % 10 == 0 =>
        val newState =
          state.modify(_.pondCirculation.cadencePercentage).setTo(percentage)
        updateSettings(newState, timestamp, force = false)

      case CadenceChanged(_) =>
        (
          state,
          restoreItem(
            config.circulationCadenceItem,
            state.pondCirculation.cadencePercentage.toString
          )
        )

      case StatusReported(rawStatus) =>
        PondCirculationStatus.fromString(rawStatus) match
          case Some(status) =>
            val newState =
              state.modify(_.pondCirculation.status).setTo(Some(status))
            (
              newState,
              Set(
                Action.SetUIItemValue(
                  config.circulationStatusItem,
                  PondCirculationStatus.toString(status)
                )
              )
            )
          case None => (state, Set.empty)

      case Event.System.StartupEvent =>
        updateSettings(state, timestamp, force = true)

      case ScheduleTransition =>
        val (newState, commandActions) =
          reconcileCommand(state, timestamp, force = false)
        (
          newState,
          commandActions ++ scheduleAction(newState, timestamp)
        )

      case _ => (state, Set.empty)
  }

  def apply(config: PondPumpConfig, zone: ZoneId): SingleProcessor =
    Impl(config, zone)
}
