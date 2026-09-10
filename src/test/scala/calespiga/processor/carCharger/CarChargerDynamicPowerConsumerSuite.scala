package calespiga.processor.carCharger

import munit.CatsEffectSuite
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
import calespiga.model.CarChargerChargingStatus

class CarChargerDynamicPowerConsumerSuite extends CatsEffectSuite {

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

    result.map(assertEquals(_, Power.zero))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when lastCommandReceived is None"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    result.map(assertEquals(_, Power.zero))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic FV and switchStatus is Off"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.Off),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      currentPowerWatts = Some(2500f)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    result.map(assertEquals(_, Power.zero))
  }

  test(
    "currentlyUsedDynamicPower: returns 0 when automatic FV and On and not status charging"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      chargingStatus = Some(CarChargerChargingStatus.Connected),
      currentPowerWatts = None
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)
    result.map(assertEquals(_, Power.zero))
  }

  test(
    "currentlyUsedDynamicPower: returns planned power when automatic FV, charging and On"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      chargingStatus = Some(CarChargerChargingStatus.Charging),
      plannedDynamicFVPower = Some(2000),
      plannedDynamicGridPower = Some(0)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    result.map(assertEquals(_, Power(2000, 0)))
  }

  test(
    "currentlyUsedDynamicPower: returns stored FV and grid power when automatic grid and On and status i charging"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      plannedDynamicFVPower = Some(1800f),
      plannedDynamicGridPower = Some(700f),
      chargingStatus = Some(CarChargerChargingStatus.Charging)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    result.map(assertEquals(_, Power(1800f, 700f)))
  }

  test(
    "currentlyUsedDynamicPower: treats missing stored grid power as zero in automatic grid"
  ) {
    val state = stateWithCarCharger(
      switchStatus = Some(CarChargerSignal.On),
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      plannedDynamicFVPower = Some(2500f),
      chargingStatus = Some(CarChargerChargingStatus.Charging)
    )

    val result = consumer.currentlyUsedDynamicPower(state, now)

    result.map(assertEquals(_, Power.ofFv(2500f)))
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

    result.map(assertEquals(_, Power.zero))
  }

  test("currentlyUsedDynamicPower: returns 0 dynamic power when NotInSyncNow") {
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

    result.map(assertEquals(_, Power.zero))
  }

  // ============================================================
  // usePower tests
  // ============================================================

  test(
    "usePower: clears planned power and returns zero power when not automatic"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOff),
      plannedDynamicFVPower = Some(1000f),
      plannedDynamicGridPower = Some(1000f)
    )

    val resultState = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOff),
      plannedDynamicFVPower = None,
      plannedDynamicGridPower = None
    )

    val result = consumer.usePower(state, Power.ofFv(3000f), now)

    result.map { result =>
      assertEquals(result.actions, Set.empty)
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state, resultState)
    }
  }

  test(
    "usePower: sets On when automatic and available power >= chargerPowerWatts"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        Some(dummyConfig.chargerPowerWatts)
      )
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(0f))
      assertEquals(result.state.carCharger.automaticOnSince, Some(now))

      assert(result.actions.nonEmpty)
      assertEquals(result.actions.size, 2)

      val mqttAction = result.actions.collectFirst {
        case a: Action.SendMqttStringMessage => a
      }.get
      assertEquals(mqttAction.topic, dummyConfig.mqttTopicForCommand)
      assertEquals(mqttAction.message, "on")
    }
  }

  test(
    "usePower: sets Off when automatic and available power < chargerPowerWatts"
  ) {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result = consumer.usePower(state, Power.ofFv(1500f), now)
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.Off)
      )
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state.carCharger.plannedDynamicFVPower, None)
      assertEquals(result.state.carCharger.plannedDynamicGridPower, None)
      assertEquals(result.state.carCharger.automaticOnSince, None)

      assert(result.actions.nonEmpty)
      assertEquals(result.actions.size, 2)

      val mqttAction = result.actions.collectFirst {
        case a: Action.SendMqttStringMessage => a
      }.get
      assertEquals(mqttAction.message, "off")
    }
  }

  test("usePower: sets On at boundary when power == chargerPowerWatts") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV)
    )

    val result =
      consumer.usePower(state, Power.ofFv(dummyConfig.chargerPowerWatts), now)
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        Some(dummyConfig.chargerPowerWatts)
      )
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(0f))
      assertEquals(result.state.carCharger.automaticOnSince, Some(now))
    }
  }

  test(
    "usePower: sets Off and uses zero power when NotInSync beyond timeout if automatic FV"
  ) {
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
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.Off)
      )
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state.carCharger.plannedDynamicFVPower, None)
      assertEquals(result.state.carCharger.plannedDynamicGridPower, None)
      assertEquals(result.state.carCharger.automaticOnSince, None)
      assert(result.actions.nonEmpty)
      assertEquals(result.actions.size, 2)

      val mqttAction = result.actions.collectFirst {
        case a: Action.SendMqttStringMessage => a
      }.get
      assertEquals(mqttAction.message, "off")
    }
  }

  test(
    "usePower: sets Off and uses zero power when NotInSync beyond timeout if automatic Grid"
  ) {
    val syncStartTime = now.minusSeconds(120)
    val consumerWithSyncDetector = CarChargerDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(checkIfInSyncStub =
        _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(3000f), now)
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.Off)
      )
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state.carCharger.plannedDynamicFVPower, None)
      assertEquals(result.state.carCharger.plannedDynamicGridPower, None)
      assertEquals(result.state.carCharger.automaticOnSince, None)
      assert(result.actions.nonEmpty)
      assertEquals(result.actions.size, 2)

      val mqttAction = result.actions.collectFirst {
        case a: Action.SendMqttStringMessage => a
      }.get
      assertEquals(mqttAction.message, "off")
    }
  }

  test(
    "usePower: does not change anything when NotInSync beyond timeout if NOT automatic"
  ) {
    val syncStartTime = now.minusSeconds(120)
    val consumerWithSyncDetector = CarChargerDynamicPowerConsumer(
      dummyConfig,
      SyncDetectorStub(checkIfInSyncStub =
        _ => calespiga.processor.utils.SyncDetector.NotInSync(syncStartTime)
      )
    )

    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.TurnOn)
    )

    val result =
      consumerWithSyncDetector.usePower(state, Power.ofFv(3000f), now)
    result.map { result =>
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        None
      )
      assertEquals(
        result.state.carCharger.plannedDynamicGridPower,
        None
      )
      assertEquals(result.state.carCharger.automaticOnSince, None)
      assertEquals(result.actions, Set.empty)
    }
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
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power.ofFv(dummyConfig.chargerPowerWatts))
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        Some(dummyConfig.chargerPowerWatts)
      )
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(0f))
      assertEquals(result.state.carCharger.automaticOnSince, Some(now))
    }
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
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power(fvPower, 500f))
      assertEquals(result.state.carCharger.plannedDynamicFVPower, Some(fvPower))
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(500f))
    }
  }

  test(
    "usePower: reports measured power and clears grace timestamp when charging"
  ) {
    val automaticOnSince = now.minusSeconds(10)
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      automaticOnSince = Some(automaticOnSince),
      chargingStatus = Some(CarChargerChargingStatus.Charging),
      currentPowerWatts = Some(1800f)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)

    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power.ofFv(1800f))
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        Some(dummyConfig.chargerPowerWatts)
      )
      assertEquals(result.state.carCharger.automaticOnSince, None)
    }
  }

  test("usePower: keeps charger on but reports zero after grace period") {
    val automaticOnSince = now.minusSeconds(31)
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticFV),
      automaticOnSince = Some(automaticOnSince),
      chargingStatus = Some(CarChargerChargingStatus.Connected)
    )

    val result = consumer.usePower(state, Power.ofFv(2500f), now)

    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.On)
      )
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(
        result.state.carCharger.plannedDynamicFVPower,
        Some(dummyConfig.chargerPowerWatts)
      )
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(0f))
      assertEquals(
        result.state.carCharger.automaticOnSince,
        Some(automaticOnSince)
      )
    }
  }
  test("usePower: grid mode excludes grid power above the configured tariff") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      maxGridTariff = Some(BatteryChargeTariff.PlaAndVall)
    )
      .modify(_.grid.currentTariff)
      .setTo(Some(GridTariff.Pic))

    val result = consumer.usePower(state, Power(1600f, 1000f), now)
    result.map { result =>
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state.carCharger.plannedDynamicGridPower, None)
    }
  }

  test("usePower: grid mode accepts the configured tariff") {
    val state = stateWithCarCharger(
      lastCommandReceived = Some(CarChargerSignal.SetAutomaticGrid),
      maxGridTariff = Some(BatteryChargeTariff.PlaAndVall)
    )
      .modify(_.grid.currentTariff)
      .setTo(Some(GridTariff.Pla))

    val result = consumer.usePower(state, Power(1600f, 500f), now)
    result.map { result =>
      assertEquals(result.powerUsed, Power(1600f, 500f))
      assertEquals(result.state.carCharger.plannedDynamicGridPower, Some(500f))
    }
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
    result.map { result =>
      assertEquals(
        result.state.carCharger.lastCommandSent,
        Some(CarChargerSignal.Off)
      )
      assertEquals(result.powerUsed, Power.zero)
      assertEquals(result.state.carCharger.plannedDynamicFVPower, None)
      assertEquals(result.state.carCharger.plannedDynamicGridPower, None)
    }
  }

}
