package calespiga.processor.heater

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.model.HeaterSignal
import calespiga.config.HeaterConfig
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import scala.concurrent.duration.DurationInt
import calespiga.processor.utils.SyncDetectorStub
import java.time.Instant

class HeaterDynamicPowerConsumerSuite extends FunSuite {

  private val dummyConfig = HeaterConfig(
    mqttTopicForCommand = "heater/command",
    lastTimeHotItem = "heater/lastTimeHot",
    energyTodayItem = "heater/energyToday",
    statusItem = "heater/status",
    isHotItem = "heater/isHot",
    resendInterval = 30.seconds,
    id = "heater-test",
    onlineStatusItem = "heater/online",
    syncStatusItem = "heater/sync",
    lastCommandItem = "heater/lastCommand",
    syncTimeoutForDynamicPower = 50.seconds
  )

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val consumer =
    new HeaterDynamicPowerConsumer(dummyConfig, SyncDetectorStub())

  private def stateWithHeater(
      status: Option[HeaterSignal.ControllerState] = None,
      lastCommandSent: Option[HeaterSignal.ControllerState] = None,
      lastCommandReceived: Option[HeaterSignal.UserCommand] = None
  ): State =
    State()
      .modify(_.heater.status)
      .setTo(
        status
      )
      .modify(_.heater.lastCommandSent)
      .setTo(
        lastCommandSent
      )
      .modify(_.heater.lastCommandReceived)
      .setTo(
        lastCommandReceived
      )

  // ============================================================
  // currentlyUsedDynamicPower tests
  // ============================================================

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is OFF"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power2000),
      lastCommandReceived = Some(HeaterSignal.TurnOff)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is 500"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power2000),
      lastCommandReceived = Some(HeaterSignal.SetPower500)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is None"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power1000)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returnsPower.ofUnusedFV(500) when automatic and status is Power500"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power500),
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(500f))
  }

  test(
    "currentlyUsedDynamicPower: returnsPower.ofUnusedFV(1000) when automatic and status is Power1000"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power1000),
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(1000f))
  }

  test(
    "currentlyUsedDynamicPower: returnsPower.ofUnusedFV(2000) when automatic and status is Power2000"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power2000),
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(2000f))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic and status is Off"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Off),
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic and status is None"
  ) {
    val state = stateWithHeater(
      status = None,
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  // ============================================================
  // usePower tests
  // ============================================================

  test(
    "usePower: returns unchanged state, no actions, and zero power when not automatic"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.TurnOff)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f))

    assertEquals(result.state, state, "State should remain unchanged")
    assertEquals(result.actions, Set.empty, "No actions should be returned")
    assertEquals(
      result.powerUsed,
      Power.zero,
      "Power used should be zero"
    )
  }

  test(
    "usePower: returns unchanged state, no actions, and zero power when lastCommandReceived is None"
  ) {
    val state = stateWithHeater()

    val result = consumer.usePower(state, Power.ofFv(1500f))

    assertEquals(result.state, state, "State should remain unchanged")
    assertEquals(result.actions, Set.empty, "No actions should be returned")
    assertEquals(
      result.powerUsed,
      Power.zero,
      "Power used should be zero"
    )
  }

  test(
    "usePower: sets Power2000 when automatic and power >= 2000"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power2000),
      "lastCommandSent should be Power2000"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(2000f),
      "Power used should be 2000"
    )

    // Check that actions are returned
    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    // Verify one is immediate MQTT and one is periodic
    val mqttActions = result.actions.collect {
      case a: Action.SendMqttStringMessage => a
    }
    val periodicActions = result.actions.collect { case a: Action.Periodic =>
      a
    }

    assertEquals(mqttActions.size, 1, "Should have one MQTT action")
    assertEquals(periodicActions.size, 1, "Should have one periodic action")

    val mqttAction = mqttActions.head
    assertEquals(mqttAction.topic, dummyConfig.mqttTopicForCommand)
    assertEquals(mqttAction.message, "2000")

    val periodicAction = periodicActions.head
    assertEquals(
      periodicAction.id,
      dummyConfig.id + Actions.COMMAND_ACTION_SUFFIX
    )
    assertEquals(periodicAction.period, dummyConfig.resendInterval)
  }

  test(
    "usePower: sets Power1000 when automatic and 1000 <= power < 2000"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(1500f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power1000),
      "lastCommandSent should be Power1000"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(1000f),
      "Power used should be 1000"
    )

    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "1000")
  }

  test(
    "usePower: sets Power500 when automatic and 500 <= power < 1000"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(750f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power500),
      "lastCommandSent should be Power500"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(500f),
      "Power used should be 500"
    )

    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "500")
  }

  test(
    "usePower: sets Off when automatic and power < 500"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(300f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Off),
      "lastCommandSent should be Off"
    )
    assertEquals(
      result.powerUsed,
      Power.zero,
      "Power used should be zero"
    )

    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "0")
  }

  test(
    "usePower: sets Power1000 at boundary (exactly 2000)"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(2000f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power1000)
    )
    assertEquals(result.powerUsed, Power.ofFv(1000f))
  }

  test(
    "usePower: sets Power500 at boundary (exactly 1000)"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(1000f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power500)
    )
    assertEquals(result.powerUsed, Power.ofFv(500f))
  }

  test(
    "usePower: sets Off at boundary (exactly 500)"
  ) {
    val state = stateWithHeater(
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(500f))

    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Off)
    )
    assertEquals(result.powerUsed, Power.zero)
  }

  test(
    "usePower: state is modified only in lastCommandSent, other fields unchanged"
  ) {
    val state = stateWithHeater(
      status = Some(HeaterSignal.Power500),
      lastCommandSent = Some(HeaterSignal.Off),
      lastCommandReceived = Some(HeaterSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(1500f))

    assertEquals(
      result.state.heater.status,
      Some(HeaterSignal.Power500),
      "Status should remain unchanged"
    )
    assertEquals(
      result.state.heater.lastCommandReceived,
      Some(HeaterSignal.SetAutomatic),
      "lastCommandReceived should remain unchanged"
    )
    assertEquals(
      result.state.heater.lastCommandSent,
      Some(HeaterSignal.Power1000),
      "Only lastCommandSent should be updated"
    )
  }
}
