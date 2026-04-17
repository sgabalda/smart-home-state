package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import com.softwaremill.quicklens.*
import java.time.ZoneId
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.EnergyCalculatorStub
import scala.collection.mutable

class CarChargerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-15T10:00:00Z")
  private val zone: ZoneId = ZoneId.systemDefault()
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

  // ======================
  // CarChargerEnergyProcessor Tests
  // ======================

  test("CarChargerPowerReported calculates energy with same day") {
    val secondsAgo = 3600
    val initialEnergy = 100f
    val calculatedEnergy = 2100f
    val oneHourAgo = now.minusSeconds(secondsAgo)
    val initialState = stateWithCarCharger(
      currentPowerWatts = Some(7000f),
      energyTodayWh = initialEnergy,
      lastPowerUpdate = Some(oneHourAgo)
    )

    // Track calls to the energy calculator
    val calculatorCalls = mutable.ListBuffer
      .empty[(Option[Instant], Instant, Int, Float, ZoneId)]
    val energyCalculator = EnergyCalculatorStub(
      calculateEnergyTodayStub =
        (lastChange, timestamp, power, energy, zone) => {
          calculatorCalls.addOne((lastChange, timestamp, power, energy, zone))
          calculatedEnergy
        }
    )

    val event = CarChargerPowerReported(6500f)
    val processor = CarChargerEnergyProcessor(config, zone, energyCalculator)
    val (newState, actions) = processor.process(initialState, event, now)

    // Verify EnergyCalculator was called with correct parameters
    assertEquals(calculatorCalls.size, 1)
    val (callLastChange, callTimestamp, callPower, callEnergy, callZone) =
      calculatorCalls.head
    assertEquals(callLastChange, Some(oneHourAgo))
    assertEquals(callTimestamp, now)
    assertEquals(callPower, 7000)
    assertEquals(callEnergy, initialEnergy)
    assertEquals(callZone, zone)

    // Verify state was updated
    assertEquals(newState.carCharger.lastPowerUpdate, Some(now))
    assertEqualsDouble(
      newState.carCharger.energyTodayWh,
      calculatedEnergy,
      0.1f
    )

    // Verify UI actions
    val expectedActions: Set[Action] = Set(
      Action.SetUIItemValue(
        config.energyTodayItem,
        calculatedEnergy.toInt.toString
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("CarChargerPowerReported resets energy if new day") {
    val initialEnergy = 10000f
    val calculatedEnergy = 1000f
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant
    val today = yesterday.plusSeconds(3600)
    val initialStateNewDay = stateWithCarCharger(
      currentPowerWatts = Some(6000f),
      energyTodayWh = initialEnergy,
      lastPowerUpdate = Some(yesterday)
    )

    // Track calls to the energy calculator
    val calculatorCalls = mutable.ListBuffer
      .empty[(Option[Instant], Instant, Int, Float, ZoneId)]
    val energyCalculator = EnergyCalculatorStub(
      calculateEnergyTodayStub =
        (lastChange, timestamp, power, energy, zone) => {
          calculatorCalls.addOne((lastChange, timestamp, power, energy, zone))
          calculatedEnergy
        }
    )

    val event = CarChargerPowerReported(7000f)
    val processor = CarChargerEnergyProcessor(config, zone, energyCalculator)
    val (newState, actions) =
      processor.process(initialStateNewDay, event, today)

    // Energy should be recalculated (reset for new day)
    assertEqualsDouble(
      newState.carCharger.energyTodayWh,
      calculatedEnergy,
      0.1f
    )
    assertEquals(newState.carCharger.lastPowerUpdate, Some(today))

    // UI should be updated with new values
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.energyTodayItem && value == calculatedEnergy.toInt.toString
        case _ => false
      }
    )
  }

  test("CarChargerPowerReported does nothing for non-power events") {
    val energyCalculator = EnergyCalculatorStub()
    val processor = CarChargerEnergyProcessor(config, zone, energyCalculator)
    val initialState = stateWithCarCharger(energyTodayWh = 500f)

    val (newState, actions) = processor.process(
      initialState,
      CarChargerStatusReported(CarChargerSignal.On),
      now
    )

    // State and actions should remain unchanged
    assertEquals(newState.carCharger.energyTodayWh, 500f)
    assertEquals(newState.carCharger.lastPowerUpdate, None)
    assertEquals(actions, Set.empty)
  }

  // ======================
  // Integration Tests (Full Processor Chain)
  // ======================

  test("Full processor chain: status then power updates") {
    val processor = CarChargerProcessor(config, zone)
    val initialState = stateWithCarCharger()

    // First, receive status update
    val statusEvent = CarChargerStatusReported(CarChargerSignal.On)
    val (stateAfterStatus, actionsAfterStatus) =
      processor.process(initialState, statusEvent, now)

    assertEquals(
      stateAfterStatus.carCharger.switchStatus,
      Some(CarChargerSignal.On)
    )
    assert(actionsAfterStatus.exists {
      case Action.SetUIItemValue(item, _) => item == config.statusItem
      case _                              => false
    })

    // Then, receive power update
    val powerEvent = CarChargerPowerReported(6500f)
    val (stateAfterPower, actionsAfterPower) =
      processor.process(stateAfterStatus, powerEvent, now)

    assertEquals(stateAfterPower.carCharger.currentPowerWatts, Some(6500f))
    assertEquals(stateAfterPower.carCharger.lastPowerUpdate, Some(now))
    // Power update actions should include both power item and energy/timestamp updates
    assert(actionsAfterPower.exists {
      case Action.SetUIItemValue(item, _) => item == config.powerItem
      case _                              => false
    })
  }

  test("Full processor chain: multiple power updates accumulate energy") {
    val processor = CarChargerProcessor(config, zone)
    val initialState = stateWithCarCharger()

    // First power update
    val time1 = now
    val powerEvent1 = CarChargerPowerReported(7000f)
    val (state1, actions1) = processor.process(initialState, powerEvent1, time1)

    assertEquals(state1.carCharger.currentPowerWatts, Some(7000f))
    assertEquals(state1.carCharger.lastPowerUpdate, Some(time1))

    // Second power update 1 hour later
    val time2 = time1.plusSeconds(3600)
    val powerEvent2 = CarChargerPowerReported(7000f)
    val (state2, actions2) = processor.process(state1, powerEvent2, time2)

    // Power should be updated
    assertEquals(state2.carCharger.currentPowerWatts, Some(7000f))
    assertEquals(state2.carCharger.lastPowerUpdate, Some(time2))
    // Energy should have been accumulated (1 hour at 7000W = 7000 Wh, but energy calculator will handle the exact calculation)
    assert(state2.carCharger.energyTodayWh > 0f)
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
