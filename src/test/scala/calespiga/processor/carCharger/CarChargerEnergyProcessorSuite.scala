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
  private val zone: ZoneId = ZoneId.of("UTC")
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

  test("CarChargerEnergyProcessor does nothing for non-energy events") {
    val processor = CarChargerEnergyProcessor(config, zone)
    val initialState = stateWithCarCharger()

    val events = List(
      CarChargerStatusReported(CarChargerSignal.On),
      CarChargerPowerReported(1500f)
    )

    events.foreach(event => {
      val (newState, actions) = processor.process(initialState, event, now)
      assertEquals(newState, initialState)
      assertEquals(actions, Set.empty)
    })
  }

  test(
    "CarChargerAccumulatedEnergyReported updates accumulated and reports delta when initial reading is present and same day"
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

    val expectedState = initialState
      .modify(_.carCharger.lastEnergyUpdate)
      .setTo(Some(now))
      .modify(_.carCharger.lastAccumulatedEnergyWh)
      .setTo(Some(currentTotal))

    assertEquals(newState, expectedState)
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          config.energyTodayItem,
          f"${(currentTotal - dayStartAccumulated)}%.1f"
        )
      )
    )

  }

  test(
    "CarChargerAccumulatedEnergyReported on new day snapshots previous accumulated if non previous accumulated"
  ) {
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant

    val today = now.atZone(zone).toLocalDate.atStartOfDay(zone).toInstant

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
      today
    )

    // accumulatedAtDayStart should be set from previous lastAccumulatedEnergyWh
    val expectedState = initialState
      .modify(_.carCharger.lastEnergyUpdate)
      .setTo(Some(today))
      .modify(_.carCharger.lastAccumulatedEnergyWh)
      .setTo(Some(currentTotal))
      .modify(_.carCharger.accumulatedAtDayStartWh)
      .setTo(Some(lastAccumulated))

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          config.energyTodayItem,
          f"${(currentTotal - lastAccumulated)}%.1f"
        )
      )
    )
    assertEquals(newState, expectedState)
  }

  test(
    "CarChargerAccumulatedEnergyReported on new day snapshots previous accumulated if previous accumulated"
  ) {
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant

    val today = now.atZone(zone).toLocalDate.atStartOfDay(zone).toInstant

    val lastAccumulated = 5000f
    val currentTotal = 5010f
    val previousDayStartAccumulated = 4900f

    val initialState = stateWithCarCharger(
      lastEnergyUpdate = Some(yesterday),
      lastAccumulatedEnergyWh = Some(lastAccumulated),
      accumulatedAtDayStartWh = Some(previousDayStartAccumulated)
    )

    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) = processor.process(
      initialState,
      CarChargerAccumulatedEnergyReported(currentTotal),
      today
    )

    // accumulatedAtDayStart should be set from previous lastAccumulatedEnergyWh
    val expectedState = initialState
      .modify(_.carCharger.lastEnergyUpdate)
      .setTo(Some(today))
      .modify(_.carCharger.lastAccumulatedEnergyWh)
      .setTo(Some(currentTotal))
      .modify(_.carCharger.accumulatedAtDayStartWh)
      .setTo(Some(lastAccumulated))

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          config.energyTodayItem,
          f"${(currentTotal - lastAccumulated)}%.1f"
        )
      )
    )
    assertEquals(newState, expectedState)
  }

  test(
    "CarChargerAccumulatedEnergyReported snapshots previous accumulated if last energy update, last accumulated and yesterday are none"
  ) {

    val currentTotal = 5010f

    val initialState = stateWithCarCharger(
      lastEnergyUpdate = None,
      lastAccumulatedEnergyWh = None,
      accumulatedAtDayStartWh = None
    )

    val processor = CarChargerEnergyProcessor(config, zone)
    val (newState, actions) = processor.process(
      initialState,
      CarChargerAccumulatedEnergyReported(currentTotal),
      now
    )

    // accumulatedAtDayStart should be set from previous lastAccumulatedEnergyWh
    val expectedState = initialState
      .modify(_.carCharger.lastEnergyUpdate)
      .setTo(Some(now))
      .modify(_.carCharger.lastAccumulatedEnergyWh)
      .setTo(Some(currentTotal))
      .modify(_.carCharger.accumulatedAtDayStartWh)
      .setTo(Some(currentTotal))

    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.energyTodayItem, "0.0"))
    )
    assertEquals(newState, expectedState)
  }

}
