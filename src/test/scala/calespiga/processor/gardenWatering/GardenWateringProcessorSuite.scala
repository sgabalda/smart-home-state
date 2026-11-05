package calespiga.processor.gardenWatering

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.GardenWatering.*
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import munit.FunSuite
import scala.concurrent.duration.*

class GardenWateringProcessorSuite extends FunSuite {

  private val config = ProcessorConfigHelper.gardenWateringConfig
  private val zone = ZoneId.of("UTC")
  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val processor = GardenWateringProcessor(config, zone)
  private val commandId = config.id + "-command"

  private def expectedCommand(pumpOn: Boolean): Set[Action] =
    Set(
      Action.SendMqttStringMessage(
        config.mqttTopicForCommand,
        if pumpOn then "start" else "stop"
      ),
      Action.Periodic(
        commandId,
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          if pumpOn then "start" else "stop"
        ),
        config.resendInterval
      )
    )

  test("manual request starts watering and schedules its timed stop") {
    val (newState, actions) =
      processor.process(State(), ManualWateringRequested("ON"), now)

    assertEquals(
      newState.gardenWatering.activeUntil,
      Some(now.plusSeconds(600))
    )
    assertEquals(newState.gardenWatering.lastWatering, Some(now))
    assertEquals(
      actions,
      expectedCommand(pumpOn = true) ++ Set(
        Action.Delayed(
          GardenWateringProcessor.STOP_ID,
          Action.SendFeedbackEvent(WateringFinished),
          10.minutes
        ),
        Action.SetUIItemValue(config.lastWateringItem, "2023-08-17 10:00"),
        Action.SetUIItemValue(config.manualStartItem, "OFF")
      )
    )
  }

  test("manual switch OFF events do not start watering") {
    val (newState, actions) =
      processor.process(State(), ManualWateringRequested("OFF"), now)

    assertEquals(newState, State())
    assertEquals(actions, Set.empty)
  }

  test(
    "manual duration settings accept positive values and reject invalid ones"
  ) {
    val (updatedState, updateActions) =
      processor.process(State(), ManualDurationChanged(15), now)
    assertEquals(updatedState.gardenWatering.manualDurationMinutes, 15)
    assertEquals(
      updateActions,
      Set[Action](Action.SetUIItemValue(config.manualDurationItem, "15"))
    )

    val (unchangedState, invalidActions) =
      processor.process(updatedState, ManualDurationChanged(0), now)
    assertEquals(unchangedState, updatedState)
    assertEquals(
      invalidActions,
      Set[Action](Action.SetUIItemValue(config.manualDurationItem, "15"))
    )
  }

  test("scheduled settings enforce the maximum interval and hour range") {
    val (updatedState, actions) =
      processor.process(State(), PeriodHoursChanged(72), now)

    assertEquals(updatedState.gardenWatering.periodHours, 72)
    assertEquals(
      updatedState.gardenWatering.nextScheduledAt,
      Some(Instant.parse("2023-08-17T22:00:00Z"))
    )
    assert(
      actions.contains(Action.SetUIItemValue(config.periodHoursItem, "72"))
    )

    val (unchangedState, invalidActions) =
      processor.process(updatedState, PeriodHoursChanged(73), now)
    assertEquals(unchangedState, updatedState)
    assertEquals(
      invalidActions,
      Set[Action](Action.SetUIItemValue(config.periodHoursItem, "72"))
    )

    val (unchangedHourState, invalidHourActions) =
      processor.process(updatedState, StartHourChanged(24), now)
    assertEquals(unchangedHourState, updatedState)
    assertEquals(
      invalidHourActions,
      Set[Action](Action.SetUIItemValue(config.startHourItem, "22"))
    )
  }

  test(
    "scheduled watering runs for its configured duration and advances schedule"
  ) {
    val initialState = State()
      .modify(_.gardenWatering.scheduledDurationMinutes)
      .setTo(15)
      .modify(_.gardenWatering.periodHours)
      .setTo(24)
      .modify(_.gardenWatering.nextScheduledAt)
      .setTo(Some(now))
    val (newState, actions) =
      processor.process(initialState, ScheduledWateringDue, now)

    assertEquals(
      newState.gardenWatering.activeUntil,
      Some(now.plusSeconds(900))
    )
    assertEquals(
      newState.gardenWatering.nextScheduledAt,
      Some(now.plusSeconds(24 * 60 * 60))
    )
    assert(
      actions.contains(
        Action.Delayed(
          GardenWateringProcessor.STOP_ID,
          Action.SendFeedbackEvent(WateringFinished),
          15.minutes
        )
      )
    )
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.lastWateringItem,
          "2023-08-17 10:00"
        )
      )
    )
    assert(
      actions.contains(
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          "start"
        )
      )
    )
  }

  test("stale schedule events do not water before the configured next run") {
    val state = State()
      .modify(_.gardenWatering.nextScheduledAt)
      .setTo(Some(now.plusSeconds(60)))

    val (newState, actions) =
      processor.process(state, ScheduledWateringDue, now)

    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test("watering completion stops the pump and periodically resends stop") {
    val runningState =
      State().modify(_.gardenWatering.activeUntil).setTo(Some(now))
    val (newState, actions) =
      processor.process(runningState, WateringFinished, now)

    assertEquals(newState.gardenWatering.activeUntil, None)
    assertEquals(actions, expectedCommand(pumpOn = false))
  }

  test("startup restores schedule and issues a safe pump command") {
    val (newState, actions) =
      processor.process(State(), Event.System.StartupEvent, now)

    assertEquals(
      newState.gardenWatering.nextScheduledAt,
      Some(Instant.parse("2023-08-17T22:00:00Z"))
    )
    assert(
      actions.contains(
        Action.Delayed(
          GardenWateringProcessor.SCHEDULE_ID,
          Action.SendFeedbackEvent(ScheduledWateringDue),
          12.hours
        )
      )
    )
    assert(
      actions.contains(
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          "stop"
        )
      )
    )
    assert(
      actions.contains(Action.SetUIItemValue(config.manualStartItem, "OFF"))
    )
  }

  test("startup resumes an active watering run with only its remaining time") {
    val activeUntil = now.plusSeconds(300)
    val nextScheduledAt = now.plusSeconds(8 * 60 * 60)
    val state = State()
      .modify(_.gardenWatering.activeUntil)
      .setTo(Some(activeUntil))
      .modify(_.gardenWatering.nextScheduledAt)
      .setTo(Some(nextScheduledAt))
    val (newState, actions) =
      processor.process(state, Event.System.StartupEvent, now)

    assertEquals(newState.gardenWatering.activeUntil, Some(activeUntil))
    assert(
      actions.contains(
        Action.Delayed(
          GardenWateringProcessor.STOP_ID,
          Action.SendFeedbackEvent(WateringFinished),
          5.minutes
        )
      )
    )
    assert(
      actions.contains(
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          "start"
        )
      )
    )
  }

  test("status reports update the visible status and last watering time") {
    val (newState, actions) =
      processor.process(State(), PumpStatusReported("on"), now)

    assertEquals(newState.gardenWatering.pumpOn, Some(true))
    assertEquals(newState.gardenWatering.lastWatering, Some(now))
    assert(actions.contains(Action.SetUIItemValue(config.statusItem, "on")))
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.lastWateringItem,
          "2023-08-17 10:00"
        )
      )
    )
    assert(
      actions.contains(
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          "stop"
        )
      )
    )
  }

  test("OpenHAB manual switch ON is decoded as a manual watering request") {
    val converter = Event.eventsOpenHabInputItemsConverter
      .find(_._1 == "RegarHortIniciarSHS")
      .map(_._2)
      .getOrElse(fail("Manual watering item has no input converter"))

    assertEquals(
      converter("ON"),
      ManualWateringRequested("ON")
    )
  }
}
