package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
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
      energyTodayWh: Float = 0.0f,
      lastPowerUpdate: Option[Instant] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus,
          currentPowerWatts,
          energyTodayWh,
          lastPowerUpdate
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
}
