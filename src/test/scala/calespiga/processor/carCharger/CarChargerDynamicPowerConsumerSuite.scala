package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{
  Action,
  BatteryChargeTariff,
  CarChargerSignal,
  GridTariff
}
import calespiga.processor.power.dynamic.Power
import calespiga.processor.utils.SyncDetectorStub
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper
import CarChargerTestHelper.stateWithCarCharger

class CarChargerDynamicPowerConsumerSuite extends FunSuite {

  private val dummyConfig = ProcessorConfigHelper.carCharger

  private val now = Instant.parse("2024-01-15T10:00:00Z")
  private val consumer =
    CarChargerDynamicPowerConsumer(dummyConfig, SyncDetectorStub())

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
    "currentlyUsedDynamicPower: returns stored FV and grid power when automatic grid and On"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      currentDynamicFVPower = Some(1800f),
      currentDynamicGridPower = Some(700f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    assertEquals(result, Power(1800f, 700f))
  }

  test(
    "currentlyUsedDynamicPower: treats missing stored grid power as zero in automatic grid"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      currentDynamicFVPower = Some(2500f)
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
    "usePower: returns unchanged state but only no power dynamic, no actions, and zero power when not automatic"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOff),
      currentDynamicFVPower = Some(1000f),
      currentDynamicGridPower = Some(1000f)
    )

    val resultState = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOff),
      currentDynamicFVPower = None,
      currentDynamicGridPower = None
    )

    val result = consumer.usePower(state, Power.ofFv(3000f), now)

    assertEquals(result.actions, Set.empty)
    assertEquals(result.powerUsed, Power.zero)
    assertEquals(result.state, resultState)
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
    assertEquals(
      result.state.carCharger.currentDynamicFVPower,
      Some(dummyConfig.chargerPowerWatts)
    )
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))

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
    assertEquals(result.state.carCharger.currentDynamicFVPower, Some(0f))
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))

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
    assertEquals(
      result.state.carCharger.currentDynamicFVPower,
      Some(dummyConfig.chargerPowerWatts)
    )
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))
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
    assertEquals(result.state.carCharger.currentDynamicFVPower, Some(0f))
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))
    assert(result.actions.nonEmpty)
    assertEquals(result.actions.size, 2)

    val mqttAction = result.actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.message, "off")
  }

  test("usePower: grid mode uses FV power first") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid)
    )

    val result = consumer.usePower(
      state,
      Power.ofFv(dummyConfig.chargerPowerWatts + 500f),
      now
    )

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.On)
    )
    assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))
    assertEquals(
      result.state.carCharger.currentDynamicFVPower,
      Some(dummyConfig.chargerPowerWatts)
    )
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))
  }

  test("usePower: grid mode fills the remaining power from the grid") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid)
    )
    val fvPower = dummyConfig.chargerPowerWatts - 500f

    val result = consumer.usePower(
      state,
      Power(fvPower, 500f),
      now
    )

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.On)
    )
    assertEquals(result.powerUsed, Power(fvPower, 500f))
    assertEquals(result.state.carCharger.currentDynamicFVPower, Some(fvPower))
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(500f))
  }

  test("usePower: grid mode excludes grid power above the configured tariff") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      maxGridTariff = Some(BatteryChargeTariff.PlaAndVall)
    )
      .modify(_.grid.currentTariff)
      .setTo(Some(GridTariff.Pic))

    val result = consumer.usePower(state, Power(1600f, 1000f), now)

    assertEquals(result.powerUsed, Power.zero)
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))
  }

  test("usePower: grid mode accepts the configured tariff") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      maxGridTariff = Some(BatteryChargeTariff.PlaAndVall)
    )
      .modify(_.grid.currentTariff)
      .setTo(Some(GridTariff.Pla))

    val result = consumer.usePower(state, Power(1600f, 500f), now)

    assertEquals(result.powerUsed, Power(1600f, 500f))
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(500f))
  }

  test(
    "usePower: grid mode stays off when FV and grid power are insufficient"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid)
    )

    val result = consumer.usePower(
      state,
      Power(dummyConfig.chargerPowerWatts - 500f, 499f),
      now
    )

    assertEquals(
      result.state.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )
    assertEquals(result.powerUsed, Power.zero)
    assertEquals(result.state.carCharger.currentDynamicFVPower, Some(0f))
    assertEquals(result.state.carCharger.currentDynamicGridPower, Some(0f))
  }

}
