package calespiga.processor.power

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.PowerAvailableProcessorConfig
import java.time.Instant
import com.softwaremill.quicklens.*
import scala.concurrent.duration.*

class PowerAvailableProcessorSuite extends FunSuite {

  val dummyConfig = PowerAvailableProcessorConfig(
    periodAlarmNoData = 5.minutes,
    periodAlarmNoProduction = 10.hours,
    fvStartingHour = 7,
    fvEndingHour = 22,
    powerAvailableItem = "PowerAvailableItem",
    powerProducedItem = "PowerProducedItem",
    powerDiscardedItem = "PowerDiscardedItem"
  )
  val processor =
    PowerAvailableProcessor(dummyConfig, java.time.ZoneId.of("UTC"))
  val now = Instant.parse("2023-08-17T10:00:00Z")

  test(
    "PowerProductionReported updates state with all power values and timestamp"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List(10.0f, 20.0f, 30.0f)
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(newState.powerProduction.powerAvailable, Some(powerAvailable))
    assertEquals(newState.powerProduction.powerProduced, Some(powerProduced))
    assertEquals(newState.powerProduction.powerDiscarded, Some(powerDiscarded))
    assertEquals(newState.powerProduction.linesPower, linesPower)
    assertEquals(newState.powerProduction.lastUpdate, Some(now))
    assertEquals(newState.powerProduction.lastProducedPower, Some(now))
  }

  test(
    "PowerProductionReported with zero power does not update lastProducedPower"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 0.0f
    val powerDiscarded = 100.5f
    val linesPower = List.empty[Float]
    val previousTimestamp = Instant.parse("2023-08-17T09:00:00Z")
    val state = State()
      .modify(_.powerProduction.lastProducedPower)
      .setTo(Some(previousTimestamp))

    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(newState.powerProduction.powerAvailable, Some(powerAvailable))
    assertEquals(newState.powerProduction.powerProduced, Some(powerProduced))
    assertEquals(newState.powerProduction.lastUpdate, Some(now))
    assertEquals(
      newState.powerProduction.lastProducedPower,
      Some(previousTimestamp),
      "lastProducedPower should not be updated when power is 0"
    )
  }

  test(
    "PowerProductionReported emits actions to update UI items with correct item names"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.powerAvailableItem,
          f"$powerAvailable%.0f"
        )
      ),
      "Should set powerAvailableItem"
    )

    assert(
      actions.contains(
        Action
          .SetUIItemValue(dummyConfig.powerProducedItem, f"$powerProduced%.0f")
      ),
      "Should set powerProducedItem"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.powerDiscardedItem,
          f"$powerDiscarded%.0f"
        )
      ),
      "Should set powerDiscardedItem"
    )
  }

  test(
    "PowerProductionReported emits delayed action for no data notification"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    val delayedAction = actions.find {
      case Action.Delayed(id, _, delay)
          if id == PowerAvailableProcessor.ALERT_NO_UPDATES_ID
            && delay == dummyConfig.periodAlarmNoData =>
        true
      case _ => false
    }

    assert(
      delayedAction.isDefined,
      s"Should contain delayed action with id ${PowerAvailableProcessor.ALERT_NO_UPDATES_ID} " +
        s"and delay ${dummyConfig.periodAlarmNoData}"
    )
  }

  test(
    "PowerProductionReported emits delayed action when outside FV hours previous day"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val now = Instant.parse("2023-08-17T23:00:00Z")
    val (newState, actions) = processor.process(state, event, now)

    val delayedAction = actions.find {
      case Action.Delayed(id, _, delay)
          if id == PowerAvailableProcessor.ALERT_NO_UPDATES_ID
            && delay == dummyConfig.periodAlarmNoData.plus(8.hours) =>
        true
      case _ => false
    }

    assert(
      delayedAction.isDefined,
      s"Should contain delayed action with id ${PowerAvailableProcessor.ALERT_NO_UPDATES_ID} " +
        s"and delay to the next day after starting hour plus ${dummyConfig.periodAlarmNoData}"
    )
  }

  test(
    "PowerProductionReported emits delayed action when outside FV hours same day"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val now = Instant.parse("2023-08-17T03:00:00Z")
    val (newState, actions) = processor.process(state, event, now)

    val delayedAction = actions.find {
      case Action.Delayed(id, _, delay)
          if id == PowerAvailableProcessor.ALERT_NO_UPDATES_ID
            && delay == dummyConfig.periodAlarmNoData.plus(
              (dummyConfig.fvStartingHour - 3).hours
            ) =>
        true
      case _ => false
    }

    assert(
      delayedAction.isDefined,
      s"Should contain delayed action with id ${PowerAvailableProcessor.ALERT_NO_UPDATES_ID} " +
        s"and delay to the same day later after starting hour plus ${dummyConfig.periodAlarmNoData}"
    )
  }

  test(
    "PowerProductionReported with power > 0 emits delayed action for no production notification"
  ) {
    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    val delayedAction = actions.find {
      case Action.Delayed(id, _, delay)
          if id == PowerAvailableProcessor.ALERT_NO_PRODUCTION_IN_HOURS_ID
            && delay == dummyConfig.periodAlarmNoProduction =>
        true
      case _ => false
    }

    assert(
      delayedAction.isDefined,
      s"Should contain delayed action with id ${PowerAvailableProcessor.ALERT_NO_PRODUCTION_IN_HOURS_ID} " +
        s"and delay ${dummyConfig.periodAlarmNoProduction} when power > 0"
    )
  }

  test(
    "PowerProductionReported with powerAvailable = 0 does not emit delayed action for no production notification"
  ) {
    val powerAvailable = 0.0f
    val powerProduced = 0.0f
    val powerDiscarded = 0.0f
    val linesPower = List.empty[Float]
    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    val delayedAction = actions.find {
      case Action.Delayed(id, _, _)
          if id == PowerAvailableProcessor.ALERT_NO_PRODUCTION_IN_HOURS_ID =>
        true
      case _ => false
    }

    assert(
      delayedAction.isEmpty,
      s"Should not contain delayed action with id ${PowerAvailableProcessor.ALERT_NO_PRODUCTION_IN_HOURS_ID} " +
        "when powerAvailable is 0"
    )
  }

  test(
    "Non-PowerProductionReported event returns state unchanged with no actions"
  ) {
    val state = State()
    val event = Event.System.StartupEvent
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(newState, state, "State should not change")
    assertEquals(actions, Set.empty, "No actions should be emitted")
  }
}
