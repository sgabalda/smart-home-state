package calespiga.processor.greyWater

import calespiga.config.GreyWaterConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.GreyWater.*
import calespiga.model.GreyWaterMode
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

private[greyWater] object GreyWaterControlProcessor {

  val SCHEDULE_ID = "grey-water-schedule"

  private final case class Impl(
      config: GreyWaterConfig,
      zone: ZoneId
  ) extends SingleProcessor {

    private val commands = CommandActions[Boolean](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToMessage = pumpOn => if pumpOn then "start" else "stop"
    )

    private def isScheduledOn(state: State, timestamp: Instant): Boolean = {
      val currentTime = timestamp.atZone(zone).toLocalTime
      val start = LocalTime.of(state.greyWater.startHour, 0)
      val end = LocalTime.of(state.greyWater.endHour, 0)

      if start == end then false
      else if start.isBefore(end) then
        !currentTime.isBefore(start) && currentTime.isBefore(end)
      else !currentTime.isBefore(start) || currentTime.isBefore(end)
    }

    private def desiredPumpState(
        state: State,
        timestamp: Instant
    ): Boolean = state.greyWater.mode match
      case GreyWaterMode.On     => true
      case GreyWaterMode.Off    => false
      case GreyWaterMode.Horari => isScheduledOn(state, timestamp)

    private def reconcilePump(
        state: State,
        timestamp: Instant,
        force: Boolean
    ): (State, Set[Action]) = {
      val desiredState = desiredPumpState(state, timestamp)
      if force || state.greyWater.pumpOn.forall(_ != desiredState) then
        (
          state.modify(_.greyWater.lastCommandSent).setTo(Some(desiredState)),
          commands.commandActionWithResend(desiredState)
        )
      else (state, Set.empty)
    }

    private def nextTransition(
        state: State,
        timestamp: Instant
    ): Option[Instant] = {
      val today = timestamp.atZone(zone).toLocalDate
      // Schedules use local wall-clock time: DST gaps shift a boundary forward
      // to the first valid time, and overlaps use the earlier offset.
      (0L to 2L)
        .flatMap { days =>
          List(state.greyWater.startHour, state.greyWater.endHour).map { hour =>
            LocalDateTime
              .of(today.plusDays(days), LocalTime.of(hour, 0))
              .atZone(zone)
              .toInstant
          }
        }
        .filter(_.isAfter(timestamp))
        .minOption
    }

    private def scheduleAction(
        state: State,
        timestamp: Instant
    ): Set[Action] =
      state.greyWater.mode match
        case GreyWaterMode.Horari
            if state.greyWater.startHour != state.greyWater.endHour =>
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
        case _ => Set(Action.Cancel(SCHEDULE_ID))

    private def scheduleDescription(state: State): String =
      state.greyWater.mode match
        case GreyWaterMode.On     => "Sempre encesa"
        case GreyWaterMode.Off    => "Sempre apagada"
        case GreyWaterMode.Horari =>
          s"Encesa de ${state.greyWater.startHour}h a ${state.greyWater.endHour}h"

    private def displaySettings(state: State): Set[Action] =
      Set(
        Action.SetUIItemValue(
          config.modeItem,
          GreyWaterMode.toString(state.greyWater.mode)
        ),
        Action.SetUIItemValue(
          config.startHourItem,
          state.greyWater.startHour.toString
        ),
        Action.SetUIItemValue(
          config.endHourItem,
          state.greyWater.endHour.toString
        ),
        Action.SetUIItemValue(
          config.scheduleDescriptionItem,
          scheduleDescription(state)
        )
      ) ++ state.greyWater.pumpOn.map { pumpOn =>
        Action.SetUIItemValue(config.statusItem, if pumpOn then "on" else "off")
      }

    private def updateSettings(
        state: State,
        timestamp: Instant,
        force: Boolean
    ): (State, Set[Action]) =
      val (newState, commandActions) = reconcilePump(state, timestamp, force)
      (
        newState,
        displaySettings(newState) ++ commandActions ++
          scheduleAction(newState, timestamp)
      )

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case ModeChanged(rawMode) =>
        GreyWaterMode.fromString(rawMode) match
          case Some(mode) =>
            val newState = state.modify(_.greyWater.mode).setTo(mode)
            updateSettings(newState, timestamp, force = true)
          case None =>
            (
              state,
              Set(
                Action.SetUIItemValue(
                  config.modeItem,
                  GreyWaterMode.toString(state.greyWater.mode)
                )
              )
            )

      case StartHourChanged(hour) if hour >= 0 && hour <= 23 =>
        val newState = state.modify(_.greyWater.startHour).setTo(hour)
        updateSettings(newState, timestamp, force = true)

      case StartHourChanged(_) =>
        (
          state,
          Set(
            Action.SetUIItemValue(
              config.startHourItem,
              state.greyWater.startHour.toString
            )
          )
        )

      case EndHourChanged(hour) if hour >= 0 && hour <= 23 =>
        val newState = state.modify(_.greyWater.endHour).setTo(hour)
        updateSettings(newState, timestamp, force = true)

      case EndHourChanged(_) =>
        (
          state,
          Set(
            Action.SetUIItemValue(
              config.endHourItem,
              state.greyWater.endHour.toString
            )
          )
        )

      case PumpStatusReported(rawStatus) =>
        rawStatus.trim.toLowerCase match
          case "on" | "off" =>
            val pumpOn = rawStatus.trim.equalsIgnoreCase("on")
            val newState = state.modify(_.greyWater.pumpOn).setTo(Some(pumpOn))
            val (commandState, commandActions) =
              reconcilePump(newState, timestamp, force = false)
            (
              commandState,
              Set(
                Action.SetUIItemValue(
                  config.statusItem,
                  if pumpOn then "on" else "off"
                )
              ) ++ commandActions
            )
          case _ => (state, Set.empty)

      case Event.System.StartupEvent =>
        updateSettings(state, timestamp, force = true)

      case ScheduleTransition =>
        val (newState, commandActions) =
          reconcilePump(state, timestamp, force = true)
        (newState, commandActions ++ scheduleAction(newState, timestamp))

      case _ => (state, Set.empty)
  }

  def apply(config: GreyWaterConfig, zone: ZoneId): SingleProcessor =
    Impl(config, zone)
}
