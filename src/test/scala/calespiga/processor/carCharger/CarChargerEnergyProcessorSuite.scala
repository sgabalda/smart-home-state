package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import java.time.ZoneId
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper

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

  test("CarChargerPowerReported updates current power only") {
    val event = CarChargerPowerReported(6500f)
    val processor = CarChargerEnergyProcessor(config, zone)
    val initialState = stateWithCarCharger(currentPowerWatts = Some(7000f))

    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(newState.carCharger.currentPowerWatts, Some(6500f))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(config.powerItem, "6500")
      )
    )
  }

  test(
    "CarChargerPowerReported preserves energy state and updates current power"
  ) {
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
      lastAccumulatedEnergyWh = Some(10000f),
      accumulatedAtDayStartWh = Some(0f),
      lastEnergyUpdate = Some(yesterday)
    )

    val event = CarChargerPowerReported(7000f)
    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) =
      processor.process(initialStateNewDay, event, today)

    assertEquals(newState.carCharger.currentPowerWatts, Some(7000f))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(config.powerItem, "7000")
      )
    )
  }

  test("CarChargerPowerReported does nothing for non-power events") {
    val processor = CarChargerEnergyProcessor(config, zone)
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

    assertEquals(
      newState.carCharger.accumulatedAtDayStartWh,
      Some(currentTotal)
    )
    assertEquals(
      newState.carCharger.lastAccumulatedEnergyWh,
      Some(currentTotal)
    )
    assertEquals(newState.carCharger.lastEnergyUpdate, Some(now))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(config.energyTodayItem, "0")
      )
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
