package calespiga.processor.gardenWatering

import calespiga.config.GardenWateringConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.GardenWatering.*
import calespiga.model.Event.Pump.CommandRequested
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.ProcessorFormatter
import com.softwaremill.quicklens.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import scala.concurrent.duration.*

object GardenWateringProcessor {

  val SCHEDULE_ID = "garden-watering-schedule"
  val STOP_ID = "garden-watering-stop"
  private val MinimumDurationMinutes = 1
  private val MaximumDurationMinutes = 60

  private final case class Impl(
      config: GardenWateringConfig,
      zone: ZoneId
  ) extends SingleProcessor {

    private def delayUntil(timestamp: Instant, dueAt: Instant): FiniteDuration =
      Math.max(Duration.between(timestamp, dueAt).toNanos, 0L).nanos

    private def scheduleAction(
        nextScheduledAt: Instant,
        timestamp: Instant
    ): Action =
      Action.Delayed(
        SCHEDULE_ID,
        Action.SendFeedbackEvent(ScheduledWateringDue),
        delayUntil(timestamp, nextScheduledAt)
      )

    private def stopAction(timestamp: Instant, activeUntil: Instant): Action =
      Action.Delayed(
        STOP_ID,
        Action.SendFeedbackEvent(WateringFinished),
        delayUntil(timestamp, activeUntil)
      )

    private def nextStartTime(timestamp: Instant, startHour: Int): Instant = {
      val today = timestamp.atZone(zone).toLocalDate
      val candidate =
        LocalDateTime
          .of(today, LocalTime.of(startHour, 0))
          .atZone(zone)
          .toInstant
      if candidate.isBefore(timestamp) then
        LocalDateTime
          .of(today.plusDays(1), LocalTime.of(startHour, 0))
          .atZone(zone)
          .toInstant
      else candidate
    }

    private def nextFutureSchedule(
        scheduledAt: Instant,
        periodHours: Int,
        timestamp: Instant
    ): Instant = {
      val periodSeconds = periodHours.toLong * 60L * 60L
      var next = scheduledAt
      while !next.isAfter(timestamp) do next = next.plusSeconds(periodSeconds)
      next
    }

    private def pumpCommandAction(pumpOn: Boolean, force: Boolean): Action =
      Action.SendFeedbackEvent(
        CommandRequested(config.pumpId, pumpOn, force)
      )

    private def startWatering(
        state: State,
        timestamp: Instant,
        durationMinutes: Int
    ): (State, Set[Action]) = {
      val activeUntil =
        timestamp.plusSeconds(durationMinutes.toLong * 60L)
      val newState = state
        .modify(_.gardenWatering.activeUntil)
        .setTo(Some(activeUntil))
        .modify(_.gardenWatering.lastWatering)
        .setTo(Some(timestamp))
      (
        newState,
        Set(
          pumpCommandAction(pumpOn = true, force = true),
          stopAction(timestamp, activeUntil),
          Action.SetUIItemValue(
            config.lastWateringItem,
            ProcessorFormatter.format(timestamp, zone)
          )
        )
      )
    }

    private def stopWatering(
        state: State
    ): (State, Set[Action]) = {
      val newState =
        state.modify(_.gardenWatering.activeUntil).setTo(None)
      (newState, Set(pumpCommandAction(pumpOn = false, force = true)))
    }

    private def updateSchedule(
        state: State,
        timestamp: Instant
    ): (State, Set[Action]) = {
      val next =
        Option.when(state.gardenWatering.scheduledEnabled)(
          nextStartTime(timestamp, state.gardenWatering.startHour)
        )
      val newState =
        state.modify(_.gardenWatering.nextScheduledAt).setTo(next)
      (
        newState,
        next.map(scheduleAction(_, timestamp)).toSet ++
          Option.when(next.isEmpty)(Action.Cancel(SCHEDULE_ID)).toSet ++ Set(
            Action.SetUIItemValue(
              config.scheduledEnabledItem,
              if state.gardenWatering.scheduledEnabled then "ON" else "OFF"
            ),
            Action.SetUIItemValue(
              config.periodHoursItem,
              state.gardenWatering.periodHours.toString
            ),
            Action.SetUIItemValue(
              config.startHourItem,
              state.gardenWatering.startHour.toString
            )
          )
      )
    }

    private def displaySettings(state: State): Set[Action] =
      Set(
        Action.SetUIItemValue(
          config.manualDurationItem,
          state.gardenWatering.manualDurationMinutes.toString
        ),
        Action.SetUIItemValue(
          config.scheduledDurationItem,
          state.gardenWatering.scheduledDurationMinutes.toString
        ),
        Action.SetUIItemValue(
          config.scheduledEnabledItem,
          if state.gardenWatering.scheduledEnabled then "ON" else "OFF"
        ),
        Action.SetUIItemValue(
          config.periodHoursItem,
          state.gardenWatering.periodHours.toString
        ),
        Action.SetUIItemValue(
          config.startHourItem,
          state.gardenWatering.startHour.toString
        ),
        Action.SetUIItemValue(
          config.lastWateringItem,
          state.gardenWatering.lastWatering
            .map(ProcessorFormatter.format(_, zone))
            .getOrElse("")
        )
      )

    private def displayValue(item: String, value: Int): Action =
      Action.SetUIItemValue(item, value.toString)

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case ManualDurationChanged(minutes)
          if minutes >= MinimumDurationMinutes &&
            minutes <= MaximumDurationMinutes =>
        val newState =
          state.modify(_.gardenWatering.manualDurationMinutes).setTo(minutes)
        (newState, Set(displayValue(config.manualDurationItem, minutes)))

      case ManualDurationChanged(_) =>
        (
          state,
          Set(
            displayValue(
              config.manualDurationItem,
              state.gardenWatering.manualDurationMinutes
            )
          )
        )

      case ScheduledDurationChanged(minutes)
          if minutes >= MinimumDurationMinutes &&
            minutes <= MaximumDurationMinutes =>
        val newState = state
          .modify(_.gardenWatering.scheduledDurationMinutes)
          .setTo(minutes)
        (newState, Set(displayValue(config.scheduledDurationItem, minutes)))

      case ScheduledDurationChanged(_) =>
        (
          state,
          Set(
            displayValue(
              config.scheduledDurationItem,
              state.gardenWatering.scheduledDurationMinutes
            )
          )
        )

      case PeriodHoursChanged(hours) if hours >= 1 && hours <= 72 =>
        val newState =
          state.modify(_.gardenWatering.periodHours).setTo(hours)
        updateSchedule(newState, timestamp)

      case PeriodHoursChanged(_) =>
        (
          state,
          Set(
            displayValue(
              config.periodHoursItem,
              state.gardenWatering.periodHours
            )
          )
        )

      case StartHourChanged(hour) if hour >= 0 && hour <= 23 =>
        val newState = state.modify(_.gardenWatering.startHour).setTo(hour)
        updateSchedule(newState, timestamp)

      case StartHourChanged(_) =>
        (
          state,
          Set(
            displayValue(config.startHourItem, state.gardenWatering.startHour)
          )
        )

      case ManualWateringRequested(command)
          if command.trim.equalsIgnoreCase("ON") ||
            command.trim.equalsIgnoreCase("true") =>
        val (newState, actions) = startWatering(
          state,
          timestamp,
          state.gardenWatering.manualDurationMinutes
        )
        (
          newState,
          actions + Action.SetUIItemValue(config.manualStartItem, "OFF")
        )

      case ManualWateringRequested(_) =>
        (state, Set.empty)

      case ScheduledWateringEnabledChanged(command)
          if command.trim.equalsIgnoreCase("ON") ||
            command.trim.equalsIgnoreCase("true") =>
        updateSchedule(
          state.modify(_.gardenWatering.scheduledEnabled).setTo(true),
          timestamp
        )

      case ScheduledWateringEnabledChanged(command)
          if command.trim.equalsIgnoreCase("OFF") ||
            command.trim.equalsIgnoreCase("false") =>
        val disabledState = state
          .modify(_.gardenWatering.scheduledEnabled)
          .setTo(false)
          .modify(_.gardenWatering.nextScheduledAt)
          .setTo(None)
        (
          disabledState,
          Set(
            Action.Cancel(SCHEDULE_ID),
            Action.SetUIItemValue(config.scheduledEnabledItem, "OFF")
          )
        )

      case ScheduledWateringEnabledChanged(_) =>
        (
          state,
          Set(
            Action.SetUIItemValue(
              config.scheduledEnabledItem,
              if state.gardenWatering.scheduledEnabled then "ON" else "OFF"
            )
          )
        )

      case Event.System.StartupEvent =>
        val activeUntil =
          state.gardenWatering.activeUntil.filter(_.isAfter(timestamp))
        val restoredState = state
          .modify(_.gardenWatering.activeUntil)
          .setTo(activeUntil)
        val nextScheduledAt =
          if restoredState.gardenWatering.scheduledEnabled then
            restoredState.gardenWatering.nextScheduledAt
              .map(
                nextFutureSchedule(
                  _,
                  restoredState.gardenWatering.periodHours,
                  timestamp
                )
              )
              .orElse(
                Some(
                  nextStartTime(
                    timestamp,
                    restoredState.gardenWatering.startHour
                  )
                )
              )
          else None
        val scheduledState = restoredState
          .modify(_.gardenWatering.nextScheduledAt)
          .setTo(nextScheduledAt)
        val pumpCommand = pumpCommandAction(
          pumpOn = activeUntil.isDefined,
          force = true
        )
        val scheduleActions =
          nextScheduledAt.map(scheduleAction(_, timestamp)).toSet ++
            Option
              .when(nextScheduledAt.isEmpty)(Action.Cancel(SCHEDULE_ID))
              .toSet
        val stopActions =
          activeUntil.map(stopAction(timestamp, _)).toSet
        (
          scheduledState,
          displaySettings(scheduledState) ++
            Set(Action.SetUIItemValue(config.manualStartItem, "OFF")) ++
            Set(pumpCommand) ++ scheduleActions ++ stopActions
        )

      case ScheduledWateringDue if !state.gardenWatering.scheduledEnabled =>
        (state, Set.empty)

      case ScheduledWateringDue
          if state.gardenWatering.nextScheduledAt.exists(
            _.isAfter(timestamp)
          ) =>
        (state, Set.empty)

      case ScheduledWateringDue =>
        val next = state.gardenWatering.nextScheduledAt
          .map(
            nextFutureSchedule(
              _,
              state.gardenWatering.periodHours,
              timestamp
            )
          )
          .getOrElse(
            timestamp.plusSeconds(
              state.gardenWatering.periodHours.toLong * 60L * 60L
            )
          )
        val scheduledState =
          state.modify(_.gardenWatering.nextScheduledAt).setTo(Some(next))
        val scheduledActions = Set(scheduleAction(next, timestamp))
        if scheduledState.gardenWatering.activeUntil.exists(
            _.isAfter(timestamp)
          )
        then (scheduledState, scheduledActions)
        else
          val (newState, wateringActions) = startWatering(
            scheduledState,
            timestamp,
            scheduledState.gardenWatering.scheduledDurationMinutes
          )
          (newState, scheduledActions ++ wateringActions)

      case WateringFinished =>
        if state.gardenWatering.activeUntil.exists(!_.isAfter(timestamp)) then
          stopWatering(state)
        else (state, Set.empty)

      case _ => (state, Set.empty)
    }
  }

  def apply(config: GardenWateringConfig, zone: ZoneId): SingleProcessor =
    Impl(config, zone)
}
