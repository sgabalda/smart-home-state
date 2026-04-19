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
      energyTodayWh: Float,
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

    assertEquals(newState.carCharger.lastPowerUpdate, Some(now))
    assertEqualsDouble(
      newState.carCharger.energyTodayWh,
      calculatedEnergy,
      0.1f
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
      energyTodayWh = initialEnergy,
      lastPowerUpdate = Some(yesterday)
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

    assertEqualsDouble(
      newState.carCharger.energyTodayWh,
      calculatedEnergy,
      0.1f
    )
    assertEquals(newState.carCharger.lastPowerUpdate, Some(today))

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

    assertEquals(newState.carCharger.energyTodayWh, 500f)
    assertEquals(newState.carCharger.lastPowerUpdate, None)
    assertEquals(actions, Set.empty)
  }
}
