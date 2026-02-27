package calespiga.processor.infraredStove

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.model.InfraredStoveSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.processor.utils.SyncDetectorStub
import java.time.Instant
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions

class InfraredStoveDynamicPowerConsumerSuite extends FunSuite {

  private val dummyConfig = ProcessorConfigHelper.infraredStoveConfig

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val consumer =
    InfraredStoveDynamicPowerConsumer(dummyConfig, SyncDetectorStub())

  private def stateWithInfraredStove(
      status: Option[InfraredStoveSignal.ControllerState] = None,
      lastCommandSent: Option[InfraredStoveSignal.ControllerState] = None,
      lastCommandReceived: Option[InfraredStoveSignal.UserCommand] = None
  ): State =
    State()
      .modify(_.infraredStove.status)
      .setTo(
        status
      )
      .modify(_.infraredStove.lastCommandSent)
      .setTo(
        lastCommandSent
      )
      .modify(_.infraredStove.lastCommandReceived)
      .setTo(
        lastCommandReceived
      )

  // ============================================================
  // currentlyUsedDynamicPower tests
  // ============================================================

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is OFF"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power1200),
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is 600"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power1200),
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is None"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns Power.ofFv(600) when automatic and status is Power600"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(600f))
  }

  test(
    "currentlyUsedDynamicPower: returns Power.ofFv(1200) when automatic and status is Power1200"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power1200),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(1200f))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic and status is Off"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Off),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic and status is None"
  ) {
    val state = stateWithInfraredStove(
      status = None,
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when NotInSync beyond timeout interval"
  ) {
    val syncStartTime =
      now.minusSeconds(60) // 60 seconds ago, beyond the 50 second timeout
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power1200),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumerWithSyncDetector.currentlyUsedDynamicPower(state, now)

    assertEquals(
      result,
      Power.zero,
      "Should return zero power when not in sync beyond timeout"
    )
  }

  test(
    "currentlyUsedDynamicPower: returns normal power when NotInSyncNow"
  ) {
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSyncNow
      )
    )

    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power1200),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumerWithSyncDetector.currentlyUsedDynamicPower(state, now)

    assertEquals(
      result,
      Power.ofFv(1200f),
      "Should return normal power when NotInSyncNow"
    )
  }

  test(
    "currentlyUsedDynamicPower: returns normal power when NotInSync within timeout"
  ) {
    val syncStartTime =
      now.minusSeconds(30) // 30 seconds ago, within the 50 second timeout
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumerWithSyncDetector.currentlyUsedDynamicPower(state, now)

    assertEquals(
      result,
      Power.ofFv(600f),
      "Should return normal power when not in sync within timeout"
    )
  }

  // ============================================================
  // usePower tests
  // ============================================================

  test(
    "usePower: returns unchanged state, no actions, and zero power when not automatic"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)

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
    val state = stateWithInfraredStove()

    val result = consumer.usePower(state, Power.ofFv(1500f), now)

    assertEquals(result.state, state, "State should remain unchanged")
    assertEquals(result.actions, Set.empty, "No actions should be returned")
    assertEquals(
      result.powerUsed,
      Power.zero,
      "Power used should be zero"
    )
  }

  test(
    "usePower: sets Power1200 when automatic and power > 1200"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power1200),
      "lastCommandSent should be Power1200"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(1200f),
      "Power used should be 1200"
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
    assertEquals(mqttAction.message, "1200")

    val periodicAction = periodicActions.head
    assertEquals(
      periodicAction.id,
      dummyConfig.id + CommandActions.COMMAND_ACTION_SUFFIX,
      "Periodic action ID should match"
    )
    assertEquals(periodicAction.period, dummyConfig.resendInterval)
  }

  test(
    "usePower: sets Power600 when automatic and 600 < power <= 1200"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(800f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power600),
      "lastCommandSent should be Power600"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(600f),
      "Power used should be 600"
    )

    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "600")
  }

  test(
    "usePower: sets Off when automatic and power <= 600"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(300f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Off),
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
    "usePower: sets Power600 at boundary (exactly 1200)"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(1200f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power600)
    )
    assertEquals(result.powerUsed, Power.ofFv(600f))
  }

  test(
    "usePower: sets Off at boundary (exactly 600)"
  ) {
    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(600f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Off)
    )
    assertEquals(result.powerUsed, Power.zero)
  }

  test(
    "usePower: state is modified only in lastCommandSent, other fields unchanged"
  ) {
    val state = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600),
      lastCommandSent = Some(InfraredStoveSignal.Off),
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result = consumer.usePower(state, Power.ofFv(1500f), now)

    assertEquals(
      result.state.infraredStove.status,
      Some(InfraredStoveSignal.Power600),
      "Status should remain unchanged"
    )
    assertEquals(
      result.state.infraredStove.lastCommandReceived,
      Some(InfraredStoveSignal.SetAutomatic),
      "lastCommandReceived should remain unchanged"
    )
    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power1200),
      "Only lastCommandSent should be updated"
    )
  }

  test(
    "usePower: sets Off and uses zero power when NotInSync beyond timeout"
  ) {
    val syncStartTime =
      now.minusSeconds(60) // 60 seconds ago, beyond the 50 second timeout
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(2500f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Off),
      "Should set Off when not in sync beyond timeout"
    )
    assertEquals(
      result.powerUsed,
      Power.zero,
      "Should use zero power when not in sync beyond timeout"
    )
    assert(result.actions.nonEmpty, "Actions should be returned")
    assertEquals(result.actions.size, 2, "Should return 2 actions")

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "0", "Should send Off command")
  }

  test(
    "usePower: works normally when NotInSyncNow"
  ) {
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSyncNow
      )
    )

    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(2500f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power1200),
      "Should set Power1200 when NotInSyncNow"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(1200f),
      "Should use normal power when NotInSyncNow"
    )

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "1200")
  }

  test(
    "usePower: works normally when NotInSync within timeout"
  ) {
    val syncStartTime =
      now.minusSeconds(30) // 30 seconds ago, within the 50 second timeout
    val consumerWithSyncDetector = InfraredStoveDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(
        checkIfInSyncStub =
          _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetAutomatic)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(800f), now)

    assertEquals(
      result.state.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power600),
      "Should set Power600 when not in sync within timeout"
    )
    assertEquals(
      result.powerUsed,
      Power.ofFv(600f),
      "Should use normal power when not in sync within timeout"
    )

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "600")
  }
}
