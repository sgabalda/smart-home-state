package calespiga.processor.power

import calespiga.config.PowerEnergyTodayConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.GridTariff
import calespiga.model.State
import calespiga.model.State.PowerEnergyToday
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId
import munit.FunSuite

class PowerEnergyTodayProcessorSuite extends FunSuite {

  private val config = PowerEnergyTodayConfig(
    homeConsumptionItem = "homeEnergy",
    gridConsumedItem = "gridConsumed",
    gridInjectedItem = "gridInjected",
    gridConsumedVallItem = "gridConsumedVall",
    gridConsumedPlaItem = "gridConsumedPla",
    gridConsumedPicItem = "gridConsumedPic",
    gridInjectedVallItem = "gridInjectedVall",
    gridInjectedPlaItem = "gridInjectedPla",
    gridInjectedPicItem = "gridInjectedPic"
  )
  private val zone = ZoneId.of("UTC")
  private val processor = PowerEnergyTodayProcessor(config, zone)

  private def process(
      state: State,
      event: Event.EventData,
      timestamp: String
  ): (State, Set[Action]) =
    processor.process(state, event, Instant.parse(timestamp))

  private def gridPower(power: Float): Event.Power.PowerStatusReported =
    Event.Power.PowerStatusReported(
      production = None,
      homeConsumption = 0f,
      powerFromNetwork = power
    )

  private def tariffChanged(
      state: State,
      tariff: GridTariff,
      timestamp: String
  ): (State, Set[Action]) =
    process(
      state.modify(_.grid.currentTariff).setTo(Some(tariff)),
      Event.Grid.GridTariffChanged(tariff),
      timestamp
    )

  test("daily home energy uses cumulative meter readings and publishes kWh") {
    val (baselineState, baselineActions) = process(
      State(),
      Event.Power.HomeConsumptionEnergyReported(1200f),
      "2026-11-02T08:00:00Z"
    )
    val (state, actions) = process(
      baselineState,
      Event.Power.HomeConsumptionEnergyReported(2450f),
      "2026-11-02T10:00:00Z"
    )

    assert(
      baselineActions.contains(Action.SetUIItemValue("homeEnergy", "0.000"))
    )
    assertEquals(state.powerManagement.energyToday.homeConsumptionWh, 1250f)
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue("homeEnergy", "1.250"))
    )
  }

  test("grid import is split between tariff periods") {
    val initialState =
      State(grid = State.Grid(currentTariff = Some(GridTariff.Vall)))
    val (withInitialReading, _) =
      process(initialState, gridPower(1000f), "2026-11-02T06:00:00Z")
    val (withPlaTariff, _) =
      tariffChanged(withInitialReading, GridTariff.Pla, "2026-11-02T08:00:00Z")
    assertEquals(
      withPlaTariff.powerManagement.energyToday.gridConsumedVallWh,
      2000f
    )
    val (withPicTariff, _) =
      tariffChanged(withPlaTariff, GridTariff.Pic, "2026-11-02T10:00:00Z")
    assertEquals(
      withPicTariff.powerManagement.energyToday.gridConsumedPlaWh,
      2000f
    )
    val (state, actions) =
      process(withPicTariff, gridPower(1000f), "2026-11-02T11:00:00Z")
    val energy = state.powerManagement.energyToday

    assertEqualsDouble(energy.gridConsumedVallWh.toDouble, 2000d, 0.1d)
    assertEqualsDouble(energy.gridConsumedPlaWh.toDouble, 2000d, 0.1d)
    assertEqualsDouble(energy.gridConsumedPicWh.toDouble, 1000d, 0.1d)
    assertEqualsDouble(energy.gridConsumedWh.toDouble, 5000d, 0.1d)
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue("gridConsumed", "5.000"),
        Action.SetUIItemValue("gridInjected", "0.000"),
        Action.SetUIItemValue("gridConsumedVall", "2.000"),
        Action.SetUIItemValue("gridConsumedPla", "2.000"),
        Action.SetUIItemValue("gridConsumedPic", "1.000"),
        Action.SetUIItemValue("gridInjectedVall", "0.000"),
        Action.SetUIItemValue("gridInjectedPla", "0.000"),
        Action.SetUIItemValue("gridInjectedPic", "0.000")
      )
    )
  }

  test("grid injection is accounted separately and by tariff") {
    val initialState =
      State(grid = State.Grid(currentTariff = Some(GridTariff.Pla)))
    val (withInitialReading, _) =
      process(initialState, gridPower(-500f), "2026-11-02T09:00:00Z")
    val (withPicTariff, _) =
      tariffChanged(withInitialReading, GridTariff.Pic, "2026-11-02T10:00:00Z")
    val (state, _) =
      process(withPicTariff, gridPower(-500f), "2026-11-02T11:00:00Z")
    val energy = state.powerManagement.energyToday

    assertEqualsDouble(energy.gridInjectedPlaWh.toDouble, 500d, 0.1d)
    assertEqualsDouble(energy.gridInjectedPicWh.toDouble, 500d, 0.1d)
    assertEqualsDouble(energy.gridInjectedWh.toDouble, 1000d, 0.1d)
    assertEquals(energy.gridConsumedWh, 0f)
  }

  test(
    "daily totals reset at midnight without losing the current grid reading"
  ) {
    val initialState =
      State(grid = State.Grid(currentTariff = Some(GridTariff.Vall)))
    val (beforeMidnight, _) =
      process(initialState, gridPower(2000f), "2026-11-01T23:00:00Z")
    val (state, _) =
      process(beforeMidnight, gridPower(2000f), "2026-11-02T01:00:00Z")
    val energy = state.powerManagement.energyToday

    assertEquals(energy.day, Some("2026-11-02"))
    assertEqualsDouble(energy.gridConsumedWh.toDouble, 2000d, 0.1d)
    assertEqualsDouble(energy.gridConsumedVallWh.toDouble, 2000d, 0.1d)
  }

  test("a new day uses the first cumulative meter reading as its baseline") {
    val oldTimestamp = Instant.parse("2026-11-01T23:59:00Z")
    val previousState = State(
      powerManagement = State.PowerManagement(
        energyToday = PowerEnergyToday(
          day = Some("2026-11-01"),
          homeConsumptionWh = 3000f,
          lastHomeMeterEnergyWh = Some(12000f),
          lastHomeMeterUpdate = Some(oldTimestamp)
        )
      )
    )
    val (state, actions) = process(
      previousState,
      Event.Power.HomeConsumptionEnergyReported(12100f),
      "2026-11-02T00:00:01Z"
    )

    assertEquals(state.powerManagement.energyToday.homeConsumptionWh, 0f)
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue("homeEnergy", "0.000"),
        Action.SetUIItemValue("gridConsumed", "0.000"),
        Action.SetUIItemValue("gridInjected", "0.000"),
        Action.SetUIItemValue("gridConsumedVall", "0.000"),
        Action.SetUIItemValue("gridConsumedPla", "0.000"),
        Action.SetUIItemValue("gridConsumedPic", "0.000"),
        Action.SetUIItemValue("gridInjectedVall", "0.000"),
        Action.SetUIItemValue("gridInjectedPla", "0.000"),
        Action.SetUIItemValue("gridInjectedPic", "0.000")
      )
    )
  }
}
