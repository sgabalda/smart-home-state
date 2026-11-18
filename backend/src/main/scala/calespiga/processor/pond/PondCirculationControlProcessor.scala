package calespiga.processor.pond

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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

private[pond] object PondCirculationControlProcessor {

  val SCHEDULE_ID = "pond-circulation-schedule"

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

    private def isWithinSchedule(state: State, hour: Int): Boolean = {
      val startHour = state.pondCirculation.startHour
      val endHour = state.pondCirculation.endHour

      if startHour <= endHour then hour >= startHour && hour <= endHour
      else hour >= startHour || hour <= endHour
    }

    private def isWithinSchedule(
        state: State,
        timestamp: Instant
    ): Boolean =
      isWithinSchedule(state, timestamp.atZone(zone).getHour)

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
      val localNow = timestamp.atZone(zone).toLocalDateTime
      val localDate = localNow.toLocalDate
      val onMinutes = state.pondCirculation.cadencePercentage / 10
      val command = desiredCommand(state, timestamp)

      def futureInstant(localTime: LocalDateTime): Option[Instant] = {
        val offsets = zone.getRules.getValidOffsets(localTime)
        val instants =
          if offsets.isEmpty then List(localTime.atZone(zone).toInstant)
          else offsets.asScala.map(localTime.toInstant).toList
        instants.filter(_.isAfter(timestamp)).minOption
      }

      def nextScheduledStart: Option[Instant] =
        (0L to 2L)
          .flatMap { day =>
            val start = LocalDateTime.of(
              localDate.plusDays(day),
              LocalTime.of(state.pondCirculation.startHour, 0)
            )
            futureInstant(start)
          }
          .find(candidate => desiredCommand(state, candidate) != Stop)

      val scheduleTransition = command match
        case DepuradoraCommand if onMinutes < 10 =>
          val cutoffMinute = (localNow.getMinute / 10) * 10 + onMinutes
          futureInstant(
            localNow
              .withMinute(cutoffMinute)
              .withSecond(0)
              .withNano(0)
          )

        case DepuradoraCommand =>
          val startHour = state.pondCirculation.startHour
          val endHour = state.pondCirculation.endHour
          if startHour == 0 && endHour == 23 then None
          else {
            val endDate =
              if startHour > endHour && localNow.getHour > endHour then
                localDate.plusDays(1)
              else localDate
            val endMinuteOfDay = (endHour + 1) % 24
            val endDateAdjusted =
              if endHour == 23 then endDate.plusDays(1) else endDate
            futureInstant(
              LocalDateTime.of(
                endDateAdjusted,
                LocalTime.of(endMinuteOfDay, 0)
              )
            )
          }

        case Stop if isWithinSchedule(state, timestamp) =>
          val nextBoundaryMinute = (localNow.getMinute / 10 + 1) * 10
          val nextBoundary =
            if nextBoundaryMinute < 60 then
              localNow
                .withMinute(nextBoundaryMinute)
                .withSecond(0)
                .withNano(0)
            else
              localNow
                .plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)

          futureInstant(nextBoundary) match
            case Some(candidate) if desiredCommand(state, candidate) != Stop =>
              Some(candidate)
            case _ => nextScheduledStart

        case Stop => nextScheduledStart

        case _ => None

      val clockTransition =
        Option(zone.getRules.nextTransition(timestamp))
          .map(_.getInstant)
          .filter(candidate => desiredCommand(state, candidate) != command)

      (scheduleTransition.toList ++ clockTransition.toList).minOption
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
