package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.processor.utils.SyncDetectorStub
import java.time.Instant
import calespiga.processor.ProcessorConfigHelper

class CarChargerDynamicPowerConsumerSuite extends FunSuite {

  private val dummyConfig = ProcessorConfigHelper.carCharger

  private val now = Instant.parse("2024-01-15T10:00:00Z")
  private val consumer =
    CarChargerDynamicPowerConsumer(dummyConfig, SyncDetectorStub())

  private def stateWithCarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      lastCommandSent: Option[CarChargerSignal.ControllerState] = None,
      lastCommandReceived: Option[CarChargerSignal.UserCommand] = None,
      currentPowerWatts: Option[Float] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus = switchStatus,
          lastCommandSent = lastCommandSent,
          lastCommandReceived = lastCommandReceived,
          lastChange = None,
          lastSyncing = None,
          currentPowerWatts = currentPowerWatts,
          lastEnergyUpdate = None,
          lastAccumulatedEnergyWh = None,
          accumulatedAtDayStartWh = None,
          online = None,
          chargingStatus = None
        )
      )

  // ============================================================
  // currentlyUsedDynamicPower tests
  // ============================================================

  test("currentlyUsedDynamicPower: returns 0 when lastCommandReceived is OFF") {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.TurnOff),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is None"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic and switchStatus is Off"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.Off),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test(
    "currentlyUsedDynamicPower: returns config chargerPowerWatts when automatic and On and no current power"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = None
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(dummyConfig.chargerPowerWatts))
  }

  test(
    "currentlyUsedDynamicPower: returns currentPowerWatts when automatic and On and reading present"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(2500f))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when NotInSync beyond timeout interval"
  ) {
    val syncStartTime = now.minusSeconds(120)
    val consumerWithSyncDetector = CarChargerDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(checkIfInSyncStub =
        _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = Some(2500f)
    )

    val result = consumerWithSyncDetector.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.zero)
  }

  test("currentlyUsedDynamicPower: returns normal power when NotInSyncNow") {
    val consumerWithSyncDetector = CarChargerDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(checkIfInSyncStub =
        _ => calespiga.processor.utils.SyncDetector.NotInSyncNow
      )
    )

    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = Some(2500f)
    )

    val result = consumerWithSyncDetector.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power.ofFv(2500f))
  }

  // ============================================================
  // usePower tests
  // ============================================================

  test(
    "usePower: returns unchanged state, no actions, and zero power when not automatic"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOff)
    )

    val result = consumer.usePower(state, Power.ofFv(3000f), now)

    assertEquals(result.state, state)
    assertEquals(result.actions, Set.empty)
    assertEquals(result.powerUsed, Power.zero)
  }

  test(
    "usePower: sets On when automatic and available power >= chargerPowerWatts"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.On)
    )
    assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))

    assert(result.actions.nonEmpty)
    assertEquals(result.actions.size, 2)

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.topic, dummyConfig.mqttTopicForCommand)
    assertEquals(mqttAction.message, "on")
  }

  test(
    "usePower: sets Off when automatic and available power < chargerPowerWatts"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result = consumer.usePower(state, Power.ofFv(1500f), now)

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )
    assertEquals(result.powerUsed, Power.zero)

    assert(result.actions.nonEmpty)
    assertEquals(result.actions.size, 2)

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "off")
  }

  test("usePower: sets On at boundary when power == chargerPowerWatts") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result =
      consumer.usePower(state, Power.ofFv(dummyConfig.chargerPowerWatts), now)

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.On)
    )
    assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))
  }

  test("usePower: sets Off and uses zero power when NotInSync beyond timeout") {
    val syncStartTime = now.minusSeconds(120)
    val consumerWithSyncDetector = CarChargerDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(checkIfInSyncStub =
        _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(3000f), now)

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )
    assertEquals(result.powerUsed, Power.zero)
    assert(result.actions.nonEmpty)
    assertEquals(result.actions.size, 2)

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "off")
  }

}
