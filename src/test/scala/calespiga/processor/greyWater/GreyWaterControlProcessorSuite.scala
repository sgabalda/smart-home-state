package calespiga.processor.greyWater

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.GreyWater.*
import calespiga.model.GreyWaterMode
import calespiga.model.OfflineOnlineSignal
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions
import calespiga.processor.utils.OfflineDetector
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import munit.FunSuite
import scala.concurrent.duration.*

class GreyWaterControlProcessorSuite extends FunSuite {

  private val config = ProcessorConfigHelper.greyWaterConfig
  private val zone = ZoneId.of("UTC")
  private val now = Instant.parse("2023-08-17T16:00:00Z")
  private val processor = GreyWaterControlProcessor(config, zone)
  private val aggregateProcessor = GreyWaterProcessor(
    config,
    zone,
    ProcessorConfigHelper.offlineDetectorConfig,
    ProcessorConfigHelper.syncDetectorConfig
  )

  private def assertCondition(condition: Boolean): Unit =
    assertEquals(condition, true)

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
      GreyWaterControlProcessor.SCHEDULE_ID,
      Action.SendFeedbackEvent(ScheduleTransition),
      delay
    )

  test("On mode starts the pump and displays the always-on description") {
    val (newState, actions) =
      processor.process(State(), ModeChanged("On"), now)

    assertEquals(newState.greyWater.mode, GreyWaterMode.On)
    assertCondition(expectedCommandActions("start").subsetOf(actions))
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(config.scheduleDescriptionItem, "Sempre encesa")
      )
    )
    assertCondition(
      actions.contains(Action.Cancel(GreyWaterControlProcessor.SCHEDULE_ID))
    )
  }

  test("Off mode stops the pump") {
    val initialState = State().modify(_.greyWater.pumpOn).setTo(Some(true))
    val (_, actions) = processor.process(initialState, ModeChanged("off"), now)

    assertCondition(expectedCommandActions("stop").subsetOf(actions))
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(config.scheduleDescriptionItem, "Sempre apagada")
      )
    )
    assertCondition(
      actions.contains(Action.Cancel(GreyWaterControlProcessor.SCHEDULE_ID))
    )
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

    assertCondition(expectedCommandActions("start").subsetOf(actions))
    assertCondition(actions.contains(scheduledTransition(2.hours)))
    assertCondition(
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

    assertCondition(expectedCommandActions("stop").subsetOf(actions))
    assertCondition(actions.contains(scheduledTransition(22.hours)))
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

    assertCondition(expectedCommandActions("start").subsetOf(actions))
    assertCondition(actions.contains(scheduledTransition(4.hours)))
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

    assertCondition(expectedCommandActions("stop").subsetOf(actions))
    assertCondition(
      actions.contains(Action.Cancel(GreyWaterControlProcessor.SCHEDULE_ID))
    )
    assertCondition(!actions.exists(_.isInstanceOf[Action.Delayed]))
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
    assertCondition(
      actions.contains(Action.SetUIItemValue(config.statusItem, "on"))
    )
    assertCondition(expectedCommandActions("stop").subsetOf(actions))
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
      GreyWaterControlProcessor(config, ZoneId.of("Europe/Madrid"))
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

    assertCondition(beforeActions.contains(scheduledTransition(30.minutes)))

    val clockChange = Instant.parse("2023-03-26T01:00:00Z")
    val (_, transitionActions) = madridProcessor.process(
      initialState,
      ScheduleTransition,
      clockChange
    )

    assertCondition(expectedCommandActions("start").subsetOf(transitionActions))
    assertCondition(transitionActions.contains(scheduledTransition(1.hour)))
  }

  test("autumn DST overlap uses the earlier boundary and keeps the window on") {
    val madridProcessor =
      GreyWaterControlProcessor(config, ZoneId.of("Europe/Madrid"))
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

    assertCondition(beforeActions.contains(scheduledTransition(30.minutes)))

    val repeatedHour = Instant.parse("2023-10-29T01:30:00Z")
    val (_, repeatedHourActions) = madridProcessor.process(
      initialState,
      ScheduleTransition,
      repeatedHour
    )

    assertCondition(
      expectedCommandActions("start").subsetOf(repeatedHourActions)
    )
    assertCondition(
      repeatedHourActions.contains(scheduledTransition(90.minutes))
    )
  }

  test(
    "aggregate tracks startup synchronization and schedules offline detection"
  ) {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.On)
    val (newState, actions) = aggregateProcessor.process(
      initialState,
      Event.System.StartupEvent,
      now
    )
    val syncId = config.id + SyncDetector.ID_SUFFIX
    val offlineId = config.id + OfflineDetector.ID_SUFFIX

    assertEquals(newState.greyWater.lastCommandSent, Some(true))
    assertEquals(newState.greyWater.lastSyncing, Some(now))
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(
          config.syncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncingText
        )
      )
    )
    assertCondition(
      actions.contains(
        Action.Delayed(
          offlineId,
          Action.SendFeedbackEvent(Event.System.OfflineDetected(offlineId)),
          ProcessorConfigHelper.offlineDetectorConfig.timeoutDuration
        )
      )
    )
    assertCondition(actions.exists {
      case Action.Delayed(`syncId`, _, _) => true
      case _                              => false
    })
  }

  test("aggregate reports online and synchronized after matching pump status") {
    val initialState = State()
      .modify(_.greyWater.mode)
      .setTo(GreyWaterMode.On)
      .modify(_.greyWater.lastCommandSent)
      .setTo(Some(true))
      .modify(_.greyWater.lastSyncing)
      .setTo(Some(now.minusSeconds(20)))
    val (newState, actions) = aggregateProcessor.process(
      initialState,
      PumpStatusReported("on"),
      now
    )

    assertEquals(newState.greyWater.pumpOn, Some(true))
    assertEquals(newState.greyWater.online, Some(OfflineOnlineSignal.Online))
    assertEquals(newState.greyWater.lastSyncing, None)
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(
          config.onlineStatusItem,
          ProcessorConfigHelper.offlineDetectorConfig.onlineText
        )
      )
    )
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(
          config.syncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncText
        )
      )
    )
    assertCondition(
      actions.contains(Action.Cancel(config.id + SyncDetector.ID_SUFFIX))
    )
  }

  test("aggregate marks microcontroller offline after its status timeout") {
    val offlineId = config.id + OfflineDetector.ID_SUFFIX
    val (newState, actions) = aggregateProcessor.process(
      State(),
      Event.System.OfflineDetected(offlineId),
      now
    )

    assertEquals(newState.greyWater.online, Some(OfflineOnlineSignal.Offline))
    assertCondition(
      actions.contains(
        Action.SetUIItemValue(
          config.onlineStatusItem,
          ProcessorConfigHelper.offlineDetectorConfig.offlineText
        )
      )
    )
  }
}
