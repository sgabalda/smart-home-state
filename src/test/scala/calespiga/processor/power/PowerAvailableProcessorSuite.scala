package calespiga.processor.power

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.config.PowerAvailableProcessorConfig
import java.time.Instant
import com.softwaremill.quicklens.*
import scala.concurrent.duration.*

class PowerAvailableProcessorSuite extends FunSuite {

  val dummyConfig = PowerAvailableProcessorConfig(
    periodAlarmWithError = 5.minutes,
    periodAlarmNoProduction = 10.hours,
    powerAvailableItem = "PowerAvailableItem",
    powerProducedItem = "PowerProducedItem",
    powerDiscardedItem = "PowerDiscardedItem",
    readingsStatusItem = "PowerReadingsStatusItem"
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

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.readingsStatusItem,
          PowerAvailableProcessor.STATUS_OK
        )
      ),
      "Should set readingsStatusItem to OK"
    )
    assertEquals(
      actions.count {
        case Action.SetUIItemValue(_, _) => true; case _ => false
      },
      4,
      "Should emit exactly 4 UI update actions"
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
    "StartupEvent resets powerProduction fields to None and updates UI items to 0"
  ) {
    // Start with a state that has power values
    val state = State()
      .modify(_.powerProduction.powerAvailable)
      .setTo(Some(100.0f))
      .modify(_.powerProduction.powerProduced)
      .setTo(Some(75.0f))
      .modify(_.powerProduction.powerDiscarded)
      .setTo(Some(25.0f))
      .modify(_.powerProduction.lastError)
      .setTo(Some(now))

    val event = Event.System.StartupEvent
    val (newState, actions) = processor.process(state, event, now)

    // Check that power fields are reset to None
    assertEquals(
      newState.powerProduction.powerAvailable,
      None,
      "powerAvailable should be reset to None"
    )
    assertEquals(
      newState.powerProduction.powerProduced,
      None,
      "powerProduced should be reset to None"
    )
    assertEquals(
      newState.powerProduction.powerDiscarded,
      None,
      "powerDiscarded should be reset to None"
    )
    assertEquals(
      newState.powerProduction.lastError,
      None,
      "lastError should be reset to None"
    )

    // Check that UI items are updated to 0 or OK
    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerAvailableItem, "0")
      ),
      "Should set powerAvailableItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerProducedItem, "0")
      ),
      "Should set powerProducedItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerDiscardedItem, "0")
      ),
      "Should set powerDiscardedItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.readingsStatusItem,
          PowerAvailableProcessor.STATUS_OK
        )
      ),
      "Should set readingsStatusItem to OK"
    )

    assertEquals(actions.size, 4, "Should emit exactly 4 UI update actions")
  }

  test(
    "PowerProductionReadingError with no previous error sets lastError and updates UI with temporary error status"
  ) {
    val state = State()
    val event = Event.Power.PowerProductionReadingError
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerProduction.lastError,
      Some(now),
      "lastError should be set to current timestamp"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerAvailableItem, "0")
      ),
      "Should set powerAvailableItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerProducedItem, "0")
      ),
      "Should set powerProducedItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(dummyConfig.powerDiscardedItem, "0")
      ),
      "Should set powerDiscardedItem to 0"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.readingsStatusItem,
          PowerAvailableProcessor.STATUS_TEMPORARY_ERROR
        )
      ),
      "Should set readingsStatusItem to temporary error status"
    )

    assertEquals(actions.size, 4, "Should emit exactly 4 UI update actions")
  }

  test(
    "PowerProductionReadingError within alarm period does not send notification"
  ) {
    val errorTimestamp = Instant.parse("2023-08-17T09:57:00Z")
    val state = State()
      .modify(_.powerProduction.lastError)
      .setTo(Some(errorTimestamp))

    val event = Event.Power.PowerProductionReadingError
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerProduction.lastError,
      Some(errorTimestamp),
      "lastError should remain unchanged"
    )

    assertEquals(
      actions.size,
      0,
      "Should not emit any actions when error is within alarm period"
    )
  }

  test(
    "PowerProductionReadingError after alarm period sends notification and updates status to continuous error"
  ) {
    val errorTimestamp = Instant.parse("2023-08-17T09:54:59Z")
    val state = State()
      .modify(_.powerProduction.lastError)
      .setTo(Some(errorTimestamp))

    val event = Event.Power.PowerProductionReadingError
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerProduction.lastError,
      Some(errorTimestamp),
      "lastError should remain unchanged"
    )

    val notificationAction = actions.find {
      case Action.SendNotification(id, _, _)
          if id == PowerAvailableProcessor.ALERT_READING_ERROR =>
        true
      case _ => false
    }

    assert(
      notificationAction.isDefined,
      s"Should contain notification action with id ${PowerAvailableProcessor.ALERT_READING_ERROR}"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.readingsStatusItem,
          PowerAvailableProcessor.STATUS_CONTINUOUS_ERROR
        )
      ),
      "Should set readingsStatusItem to continuous error status"
    )

    assertEquals(
      actions.size,
      2,
      "Should emit notification and status update actions"
    )
  }

  test(
    "PowerProductionReported after previous error clears lastError and sets status to OK"
  ) {
    val errorTimestamp = Instant.parse("2023-08-17T09:30:00Z")
    val state = State()
      .modify(_.powerProduction.lastError)
      .setTo(Some(errorTimestamp))

    val powerAvailable = 100.5f
    val powerProduced = 75.3f
    val powerDiscarded = 25.2f
    val linesPower = List.empty[Float]
    val event = Event.Power.PowerProductionReported(
      powerAvailable,
      powerProduced,
      powerDiscarded,
      linesPower
    )
    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerProduction.lastError,
      None,
      "lastError should be cleared after successful reading"
    )

    assertEquals(
      newState.powerProduction.powerAvailable,
      Some(powerAvailable),
      "powerAvailable should be updated"
    )

    assert(
      actions.contains(
        Action.SetUIItemValue(
          dummyConfig.readingsStatusItem,
          PowerAvailableProcessor.STATUS_OK
        )
      ),
      "Should set readingsStatusItem to OK to indicate recovery from error"
    )
  }
}
