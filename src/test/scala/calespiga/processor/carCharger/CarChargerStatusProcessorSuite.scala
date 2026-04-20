package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.model.CarChargerChargingStatus
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper

class CarChargerStatusProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-15T10:00:00Z")
  private val config = ProcessorConfigHelper.carCharger

  // ======================
  // Test infrastructure
  // ======================

  private def stateWithCarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      currentPowerWatts: Option[Float] = None,
      lastEnergyUpdate: Option[Instant] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus = switchStatus,
          currentPowerWatts = currentPowerWatts,
          lastEnergyUpdate = lastEnergyUpdate
        )
      )

  // ======================
  // CarChargerStatusProcessor Tests
  // ======================

  test(
    "CarChargerStatusReported updates switch status and sends UI action"
  ) {
    val processor = CarChargerStatusProcessor(config)
    val initialState = stateWithCarCharger()

    val event = CarChargerStatusReported(CarChargerSignal.On)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(newState.carCharger.switchStatus, Some(CarChargerSignal.On))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.statusItem && value == "on"
        case _ => false
      }
    )
  }

  test("CarChargerStatusReported handles Off state") {
    val processor = CarChargerStatusProcessor(config)
    val initialState =
      stateWithCarCharger(switchStatus = Some(CarChargerSignal.On))

    val event = CarChargerStatusReported(CarChargerSignal.Off)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(newState.carCharger.switchStatus, Some(CarChargerSignal.Off))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.statusItem && value == "off"
        case _ => false
      }
    )
  }

  test("CarChargerPowerReported updates power and sends UI action") {
    val processor = CarChargerStatusProcessor(config)
    val initialState = stateWithCarCharger()

    val event = CarChargerPowerReported(7500.5f)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(newState.carCharger.currentPowerWatts, Some(7500.5f))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.powerItem && value == "7500"
        case _ => false
      }
    )
  }

  test("CarChargerPowerReported with zero power") {
    val processor = CarChargerStatusProcessor(config)
    val initialState = stateWithCarCharger()

    val event = CarChargerPowerReported(0f)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(newState.carCharger.currentPowerWatts, Some(0f))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.powerItem && value == "0"
        case _ => false
      }
    )
  }

  test("Status processor ignores non-status events") {
    val processor = CarChargerStatusProcessor(config)
    val initialState = stateWithCarCharger()

    val (newState, actions) = processor.process(
      initialState,
      CarChargerPowerReported(5000f),
      now
    )

    // Switch status should not be updated
    assertEquals(
      newState.carCharger.switchStatus,
      initialState.carCharger.switchStatus
    )
    // But power should be updated
    assertEquals(newState.carCharger.currentPowerWatts, Some(5000f))
  }

  test(
    "Power 0.0 requires two consecutive readings to set Disabled charging status"
  ) {
    val processor = CarChargerStatusProcessor(config)

    val (state1, actions1) = processor.process(
      stateWithCarCharger(),
      CarChargerPowerReported(0f),
      now
    )

    // first reading updates power but not chargingStatus
    assertEquals(state1.carCharger.currentPowerWatts, Some(0f))
    assertEquals(state1.carCharger.chargingStatus, None)
    assert(
      actions1.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.powerItem && value == "0"
        case _ => false
      }
    )

    val (state2, actions2) = processor.process(
      state1,
      CarChargerPowerReported(0f),
      now.plusSeconds(1)
    )

    // second consecutive reading in same bucket sets chargingStatus
    assertEquals(
      state2.carCharger.chargingStatus,
      Some(CarChargerChargingStatus.Disabled)
    )
    assert(
      actions2.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.chargingStatusItem && value == "disabled"
        case _ => false
      }
    )
  }

  test(
    "Power equal IDLE_POWER requires two consecutive readings to set Connected charging status"
  ) {
    val processor = CarChargerStatusProcessor(config)

    val (state1, actions1) = processor.process(
      stateWithCarCharger(),
      CarChargerPowerReported(CarChargerStatusProcessor.IDLE_POWER),
      now
    )

    assertEquals(state1.carCharger.chargingStatus, None)
    assert(
      actions1.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.powerItem && value == CarChargerStatusProcessor.IDLE_POWER.toInt.toString
        case _ => false
      }
    )

    val (state2, actions2) = processor.process(
      state1,
      CarChargerPowerReported(CarChargerStatusProcessor.IDLE_POWER),
      now.plusSeconds(1)
    )

    assertEquals(
      state2.carCharger.chargingStatus,
      Some(CarChargerChargingStatus.Connected)
    )
    assert(
      actions2.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.chargingStatusItem && value == "connected"
        case _ => false
      }
    )
  }

  test(
    "Power equal BLOCKED_POWER requires two consecutive readings to set Blocked charging status"
  ) {
    val processor = CarChargerStatusProcessor(config)

    val (state1, _) = processor.process(
      stateWithCarCharger(),
      CarChargerPowerReported(CarChargerStatusProcessor.BLOCKED_POWER),
      now
    )

    assertEquals(state1.carCharger.chargingStatus, None)

    val (state2, actions2) = processor.process(
      state1,
      CarChargerPowerReported(CarChargerStatusProcessor.BLOCKED_POWER),
      now.plusSeconds(1)
    )

    assertEquals(
      state2.carCharger.chargingStatus,
      Some(CarChargerChargingStatus.Blocked)
    )
    assert(
      actions2.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.chargingStatusItem && value == "blocked"
        case _ => false
      }
    )
  }

  test(
    "Power above BLOCKED_POWER requires two consecutive readings to set Charging charging status"
  ) {
    val processor = CarChargerStatusProcessor(config)
    val power = CarChargerStatusProcessor.BLOCKED_POWER + 1.0f

    val (state1, _) = processor.process(
      stateWithCarCharger(),
      CarChargerPowerReported(power),
      now
    )

    assertEquals(state1.carCharger.chargingStatus, None)

    val (state2, actions2) = processor.process(
      state1,
      CarChargerPowerReported(power),
      now.plusSeconds(1)
    )

    assertEquals(
      state2.carCharger.chargingStatus,
      Some(CarChargerChargingStatus.Charging)
    )
    assert(
      actions2.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.chargingStatusItem && value == "charging"
        case _ => false
      }
    )
  }
}
