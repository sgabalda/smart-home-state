package calespiga.processor.greyWater

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.GreyWater.*
import calespiga.model.GreyWaterMode
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import munit.FunSuite
import scala.concurrent.duration.*

class GreyWaterProcessorSuite extends FunSuite {

  private val config = ProcessorConfigHelper.greyWaterConfig
  private val zone = ZoneId.of("UTC")
  private val now = Instant.parse("2023-08-17T16:00:00Z")
  private val processor = GreyWaterProcessor(config, zone)

  private def expectedCommandActions(command: String): Set[Action] = {
    val mqttAction =
      Action.SendMqttStringMessage(config.mqttTopicForCommand, command)
    Set(
      mqttAction,
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        mqttAction,
        config.resendInterval
      )
    )
  }

  private def scheduledTransition(delay: FiniteDuration): Action =
    Action.Delayed(
      GreyWaterProcessor.SCHEDULE_ID,
      Action.SendFeedbackEvent(ScheduleTransition),
      delay
    )

  test("On mode starts the pump and displays the always-on description") {
    val (newState, actions) =
      processor.process(State(), ModeChanged("On"), now)

    assertEquals(newState.greyWater.mode, GreyWaterMode.On)
    assert(expectedCommandActions("start").subsetOf(actions))
    assert(
      actions.contains(
        Action.SetUIItemValue(config.scheduleDescriptionItem, "Sempre encesa")
      )
    )
    assert(actions.contains(Action.Cancel(GreyWaterProcessor.SCHEDULE_ID)))
  }

  test("Off mode stops the pump") {
    val initialState = State().modify(_.greyWater.pumpOn).setTo(Some(true))
    val (_, actions) = processor.process(initialState, ModeChanged("off"), now)

    assert(expectedCommandActions("stop").subsetOf(actions))
    assert(
      actions.contains(
        Action.SetUIItemValue(config.scheduleDescriptionItem, "Sempre apagada")
      )
    )
    assert(actions.contains(Action.Cancel(GreyWaterProcessor.SCHEDULE_ID)))
  }

  test("Horari starts within the inclusive window and schedules its end") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.startHour)
      .setTo(16)
      .modify(_.greyWater.endHour)
      .setTo(18)
    val (_, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assert(expectedCommandActions("start").subsetOf(actions))
    assert(actions.contains(scheduledTransition(2.hours)))
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.scheduleDescriptionItem,
          "Encesa de 16h a 18h"
        )
      )
    )
  }

  test("schedule transition stops at the end and schedules the next start") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.pumpOn)
      .setTo(Some(true))
    val endOfWindow = Instant.parse("2023-08-17T18:00:00Z")
    val (_, actions) =
      processor.process(initialState, ScheduleTransition, endOfWindow)

    assert(expectedCommandActions("stop").subsetOf(actions))
    assert(actions.contains(scheduledTransition(22.hours)))
  }

  test("overnight schedule remains on after midnight") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.startHour)
      .setTo(22)
      .modify(_.greyWater.endHour)
      .setTo(6)
    val afterMidnight = Instant.parse("2023-08-18T02:00:00Z")
    val (_, actions) =
      processor.process(initialState, ScheduleTransition, afterMidnight)

    assert(expectedCommandActions("start").subsetOf(actions))
    assert(actions.contains(scheduledTransition(4.hours)))
  }

  test(
    "equal schedule hours keep the pump off and cancel scheduled transitions"
  ) {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.startHour)
      .setTo(16)
      .modify(_.greyWater.endHour)
      .setTo(16)
      .modify(_.greyWater.pumpOn)
      .setTo(Some(true))
    val (_, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assert(expectedCommandActions("stop").subsetOf(actions))
    assert(actions.contains(Action.Cancel(GreyWaterProcessor.SCHEDULE_ID)))
    assert(!actions.exists(_.isInstanceOf[Action.Delayed]))
  }

  test("hours outside 0-23 are rejected and the displayed value is restored") {
    val initialState = State()
    val (newState, actions) =
      processor.process(initialState, StartHourChanged(24), now)

    assertEquals(newState, initialState)
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.startHourItem, "16"))
    )
  }

  test("end hour outside 0-23 is rejected without changing state") {
    val initialState = State()
    val (newState, actions) =
      processor.process(initialState, EndHourChanged(-1), now)

    assertEquals(newState, initialState)
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.endHourItem, "18"))
    )
  }

  test("invalid mode is ignored and the current selection is restored") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
    val (newState, actions) =
      processor.process(initialState, ModeChanged("automatic"), now)

    assertEquals(newState, initialState)
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.modeItem, "horari"))
    )
  }

  test("pump status is persisted and shown in OpenHAB") {
    val (newState, actions) =
      processor.process(State(), PumpStatusReported("on"), now)

    assertEquals(newState.greyWater.pumpOn, Some(true))
    assert(actions.contains(Action.SetUIItemValue(config.statusItem, "on")))
    assert(expectedCommandActions("stop").subsetOf(actions))
  }

  test("unknown pump status is ignored") {
    val initialState = State()
    val (newState, actions) =
      processor.process(initialState, PumpStatusReported("running"), now)

    assertEquals(newState, initialState)
    assertEquals(actions, Set.empty[Action])
  }

  test("matching pump status updates state without resending the command") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Off)
    val (newState, actions) =
      processor.process(initialState, PumpStatusReported("off"), now)

    assertEquals(newState.greyWater.pumpOn, Some(false))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "off"))
    )
  }

  test("matching on status updates state without resending the command") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.On)
    val (newState, actions) =
      processor.process(initialState, PumpStatusReported("on"), now)

    assertEquals(newState.greyWater.pumpOn, Some(true))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "on"))
    )
  }

  test("spring DST gap moves a missing schedule boundary forward") {
    val madridProcessor =
      GreyWaterProcessor(config, ZoneId.of("Europe/Madrid"))
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.startHour)
      .setTo(2)
      .modify(_.greyWater.endHour)
      .setTo(4)
    val beforeClockChange = Instant.parse("2023-03-26T00:30:00Z")
    val (_, beforeActions) = madridProcessor.process(
      initialState,
      Event.System.StartupEvent,
      beforeClockChange
    )

    assert(beforeActions.contains(scheduledTransition(30.minutes)))

    val clockChange = Instant.parse("2023-03-26T01:00:00Z")
    val (_, transitionActions) = madridProcessor.process(
      initialState,
      ScheduleTransition,
      clockChange
    )

    assert(expectedCommandActions("start").subsetOf(transitionActions))
    assert(transitionActions.contains(scheduledTransition(1.hour)))
  }

  test("autumn DST overlap uses the earlier boundary and keeps the window on") {
    val madridProcessor =
      GreyWaterProcessor(config, ZoneId.of("Europe/Madrid"))
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.Horari)
      .modify(_.greyWater.startHour)
      .setTo(2)
      .modify(_.greyWater.endHour)
      .setTo(4)
    val beforeClockChange = Instant.parse("2023-10-28T23:30:00Z")
    val (_, beforeActions) = madridProcessor.process(
      initialState,
      Event.System.StartupEvent,
      beforeClockChange
    )

    assert(beforeActions.contains(scheduledTransition(30.minutes)))

    val repeatedHour = Instant.parse("2023-10-29T01:30:00Z")
    val (_, repeatedHourActions) = madridProcessor.process(
      initialState,
      ScheduleTransition,
      repeatedHour
    )

    assert(expectedCommandActions("start").subsetOf(repeatedHourActions))
    assert(repeatedHourActions.contains(scheduledTransition(90.minutes)))
  }
}
