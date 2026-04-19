package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import java.time.ZoneId
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.EnergyCalculatorStub
import scala.collection.mutable

class CarChargerEnergyProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-15T10:00:00Z")
  private val zone: ZoneId = ZoneId.systemDefault()
  private val config = ProcessorConfigHelper.carCharger

  private def stateWithCarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      currentPowerWatts: Option[Float] = None,
      lastEnergyUpdate: Option[Instant] = None,
      lastAccumulatedEnergyWh: Option[Float] = None,
      accumulatedAtDayStartWh: Option[Float] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus = switchStatus,
          currentPowerWatts = currentPowerWatts,
          lastEnergyUpdate = lastEnergyUpdate,
          lastAccumulatedEnergyWh = lastAccumulatedEnergyWh,
          accumulatedAtDayStartWh = accumulatedAtDayStartWh
        )
      )

  test("CarChargerPowerReported calculates energy with same day") {
    val secondsAgo = 3600
    val initialEnergy = 100f
    val calculatedEnergy = 2100f
    val oneHourAgo = now.minusSeconds(secondsAgo)
    val initialState = stateWithCarCharger(
      currentPowerWatts = Some(7000f),
      lastAccumulatedEnergyWh = Some(initialEnergy),
      accumulatedAtDayStartWh = Some(0f),
      lastEnergyUpdate = Some(oneHourAgo)
    )

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

    assertEquals(calculatorCalls.size, 1)
    val (callLastChange, callTimestamp, callPower, callEnergy, callZone) =
      calculatorCalls.head
    assertEquals(callLastChange, Some(oneHourAgo))
    assertEquals(callTimestamp, now)
    assertEquals(callPower, 7000)
    assertEquals(callEnergy, initialEnergy)
    assertEquals(callZone, zone)

    assertEquals(newState.carCharger.lastEnergyUpdate, Some(now))
    // UI updated with calculated energy
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.energyTodayItem && value == calculatedEnergy.toInt.toString
        case _ => false
      }
    )

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
      lastAccumulatedEnergyWh = Some(initialEnergy),
      accumulatedAtDayStartWh = Some(0f),
      lastEnergyUpdate = Some(yesterday)
    )

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

    assertEquals(newState.carCharger.lastEnergyUpdate, Some(today))
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
    val initialState = stateWithCarCharger()

    val (newState, actions) = processor.process(
      initialState,
      CarChargerStatusReported(CarChargerSignal.On),
      now
    )

    assertEquals(newState.carCharger.lastEnergyUpdate, None)
    assertEquals(actions, Set.empty)
  }

  test(
    "CarChargerAccumulatedEnergyReported updates accumulated and reports delta"
  ) {
    val initialAccumulated = 10000f
    val dayStartAccumulated = 9900f
    val currentTotal = 10100f

    val initialState = stateWithCarCharger(
      lastEnergyUpdate = Some(now.minusSeconds(3600)),
      lastAccumulatedEnergyWh = Some(initialAccumulated),
      accumulatedAtDayStartWh = Some(dayStartAccumulated)
    )

    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) = processor.process(
      initialState,
      CarChargerAccumulatedEnergyReported(currentTotal),
      now
    )

    assertEquals(newState.carCharger.lastEnergyUpdate, Some(now))
    assertEquals(
      newState.carCharger.lastAccumulatedEnergyWh,
      Some(currentTotal)
    )
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.energyTodayItem && value == (currentTotal - dayStartAccumulated).toInt.toString
        case _ => false
      }
    )
  }

  test(
    "CarChargerAccumulatedEnergyReported initializes day-start baseline on first same-day report"
  ) {
    val currentTotal = 12000f

    val initialState = stateWithCarCharger(
      lastEnergyUpdate = Some(now.minusSeconds(3600)),
      lastAccumulatedEnergyWh = Some(currentTotal),
      accumulatedAtDayStartWh = None
    )

    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) = processor.process(
      initialState,
      CarChargerAccumulatedEnergyReported(currentTotal),
      now
    )

    assertEquals(newState.carCharger.accumulatedAtDayStartWh, Some(currentTotal))
    assertEquals(newState.carCharger.lastAccumulatedEnergyWh, Some(currentTotal))
    assertEquals(newState.carCharger.lastEnergyUpdate, Some(now))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.energyTodayItem && value == "0"
        case _ => false
      }
    )
  }

  test(
    "CarChargerAccumulatedEnergyReported on new day snapshots previous accumulated"
  ) {
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant

    val lastAccumulated = 5000f
    val currentTotal = 5010f

    val initialState = stateWithCarCharger(
      lastEnergyUpdate = Some(yesterday),
      lastAccumulatedEnergyWh = Some(lastAccumulated),
      accumulatedAtDayStartWh = None
    )

    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) = processor.process(
      initialState,
      CarChargerAccumulatedEnergyReported(currentTotal),
      now
    )

    // accumulatedAtDayStart should be set from previous lastAccumulatedEnergyWh
    assertEquals(
      newState.carCharger.accumulatedAtDayStartWh,
      Some(lastAccumulated)
    )
    assertEquals(
      newState.carCharger.lastAccumulatedEnergyWh,
      Some(currentTotal)
    )
    assertEquals(newState.carCharger.lastEnergyUpdate, Some(now))
    assert(
      actions.exists {
        case Action.SetUIItemValue(item, value) =>
          item == config.energyTodayItem && value == (currentTotal - lastAccumulated).toInt.toString
        case _ => false
      }
    )
  }
}
