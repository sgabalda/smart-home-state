package calespiga.processor.battery

import munit.FunSuite
import calespiga.model._
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.processor.grid.GridConnectionManager

class BatteryProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.batteryConfig

  // ======================
  // Test infrastructure
  // ======================

  private class ManagerStub extends GridConnectionManager {
    var requestCalls: List[GridSignal.ActorsConnecting] = Nil
    var releaseCalls: List[GridSignal.ActorsConnecting] = Nil

    override def requestConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      requestCalls = requestCalls :+ actor
      (state, Set.empty)
    }

    override def releaseConnection(
        actor: GridSignal.ActorsConnecting,
        state: State
    ): (State, Set[Action]) = {
      releaseCalls = releaseCalls :+ actor
      (state, Set.empty)
    }

    override def applyConnection(state: State): (State, Set[Action]) =
      (state, Set.empty)
  }

  private def processor(manager: ManagerStub) =
    BatteryChargeProcessor(config, manager)

  private def baseState =
    State()

  private def withTariff(state: State, tariff: GridTariff) =
    state.modify(_.grid.currentTariff).setTo(Some(tariff))

  private def withStatus(state: State, status: BatteryStatus) =
    state.modify(_.battery.status).setTo(Some(status))

  private def withChargeTariff(
      state: State,
      status: BatteryStatus,
      tariff: BatteryChargeTariff
  ) =
    status match
      case BatteryStatus.Low =>
        state
          .modify(_.battery.lowChargeTariff)
          .setTo(Some(tariff))
          .modify(_.battery.mediumChargeTariff)
          .setTo(None)

      case BatteryStatus.Medium =>
        state
          .modify(_.battery.lowChargeTariff)
          .setTo(None)
          .modify(_.battery.mediumChargeTariff)
          .setTo(Some(tariff))

      case BatteryStatus.High =>
        state
          .modify(_.battery.lowChargeTariff)
          .setTo(None)
          .modify(_.battery.mediumChargeTariff)
          .setTo(None)

  private def assertManager(manager: ManagerStub, shouldConnect: Boolean) =
    if shouldConnect then
      assertEquals(manager.requestCalls, List(GridSignal.Batteries))
      assertEquals(manager.releaseCalls, Nil)
    else
      assertEquals(manager.releaseCalls, List(GridSignal.Batteries))
      assertEquals(manager.requestCalls, Nil)

  // ======================
  // Basic behavior tests
  // ======================

  test("Battery status reported updates state and UI") {
    val manager = new ManagerStub()
    val p = processor(manager)

    val (newState, actions) = p.process(
      baseState,
      Event.Battery.BatteryStatusReported(BatteryStatus.Low),
      now
    )

    assertEquals(newState.battery.status, Some(BatteryStatus.Low))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "low"))
    )
  }

  test("Battery low tariff change updates state and UI") {
    val manager = new ManagerStub()
    val p = processor(manager)

    val (newState, actions) = p.process(
      baseState,
      Event.Battery.BatteryChargeLowTariffChanged(BatteryChargeTariff.Vall),
      now
    )

    assertEquals(
      newState.battery.lowChargeTariff,
      Some(BatteryChargeTariff.Vall)
    )
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.lowChargeTariffItem, "vall"))
    )
  }

  // ======================
  // Scenario definitions
  // ======================

  case class Scenario(
      status: BatteryStatus,
      tariff: BatteryChargeTariff,
      shouldConnect: GridTariff => Boolean
  )

  private val always: GridTariff => Boolean = _ => true
  private val never: GridTariff => Boolean = _ => false
  private val vallOnly: GridTariff => Boolean = _ == GridTariff.Vall
  private val plaAndVall: GridTariff => Boolean =
    t => t == GridTariff.Vall || t == GridTariff.Pla

  private val scenarios = List(
    Scenario(BatteryStatus.Low, BatteryChargeTariff.AllTariffs, always),
    Scenario(BatteryStatus.Low, BatteryChargeTariff.PlaAndVall, plaAndVall),
    Scenario(BatteryStatus.Low, BatteryChargeTariff.Vall, vallOnly),
    Scenario(BatteryStatus.Low, BatteryChargeTariff.NoneCharge, never),
    Scenario(BatteryStatus.Medium, BatteryChargeTariff.AllTariffs, always),
    Scenario(BatteryStatus.Medium, BatteryChargeTariff.PlaAndVall, plaAndVall),
    Scenario(BatteryStatus.Medium, BatteryChargeTariff.Vall, vallOnly),
    Scenario(BatteryStatus.Medium, BatteryChargeTariff.NoneCharge, never),
    Scenario(BatteryStatus.High, BatteryChargeTariff.AllTariffs, never)
  )

  private val tariffs = GridTariff.values

  // ======================
  // Core behavior tests
  // ======================

  tariffs.foreach { gridTariff =>
    scenarios.foreach { s =>
      test(
        s"Grid change: tariff=$gridTariff status=${s.status} config=${s.tariff}"
      ) {

        val manager = new ManagerStub()
        val p = processor(manager)

        val initialState =
          withChargeTariff(
            withStatus(withTariff(baseState, gridTariff), s.status),
            s.status,
            s.tariff
          )

        val (_, actions) = p.process(
          initialState,
          Event.Grid.GridTariffChanged(gridTariff),
          now
        )

        assertEquals(actions, Set.empty)
        assertManager(manager, s.shouldConnect(gridTariff))
      }
    }
  }

  tariffs.foreach { gridTariff =>
    scenarios.foreach { s =>
      test(s"Status change: tariff=$gridTariff newStatus=${s.status}") {

        val manager = new ManagerStub()
        val p = processor(manager)

        val initialState =
          withChargeTariff(
            withTariff(baseState, gridTariff),
            s.status,
            s.tariff
          )

        val (stateAfter, actions) = p.process(
          initialState,
          Event.Battery.BatteryStatusReported(s.status),
          now
        )

        assertEquals(stateAfter.battery.status, Some(s.status))
        assertEquals(
          actions,
          Set[Action](Action.SetUIItemValue(config.statusItem, s.status.label))
        )

        assertManager(manager, s.shouldConnect(gridTariff))
      }
    }
  }

  tariffs.foreach { gridTariff =>
    scenarios.foreach { s =>
      test(
        s"Relevant tariff change: tariff=$gridTariff status=${s.status} config=${s.tariff}"
      ) {

        val manager = new ManagerStub()
        val p = processor(manager)

        val initialState =
          withStatus(withTariff(baseState, gridTariff), s.status)

        val event = s.status match
          case BatteryStatus.Low =>
            Event.Battery.BatteryChargeLowTariffChanged(s.tariff)
          case BatteryStatus.Medium =>
            Event.Battery.BatteryChargeMediumTariffChanged(s.tariff)
          case BatteryStatus.High =>
            // High ignores tariff but we still test behavior consistency
            Event.Battery.BatteryChargeLowTariffChanged(s.tariff)

        val (stateAfter, actions) =
          p.process(initialState, event, now)

        val expectedActions = s.status match
          case BatteryStatus.Low =>
            Set[Action](
              Action.SetUIItemValue(config.lowChargeTariffItem, s.tariff.label)
            )
          case BatteryStatus.Medium =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                s.tariff.label
              )
            )
          case BatteryStatus.High =>
            Set[Action](
              Action.SetUIItemValue(config.lowChargeTariffItem, s.tariff.label)
            )

        assertEquals(actions, expectedActions)

        assertManager(manager, s.shouldConnect(gridTariff))
      }
    }
  }

  tariffs.foreach { gridTariff =>
    scenarios.foreach { s =>
      test(
        s"Irrelevant tariff change: tariff=$gridTariff status=${s.status} config=${s.tariff}"
      ) {

        val manager = new ManagerStub()
        val p = processor(manager)

        val initialState =
          withStatus(withTariff(baseState, gridTariff), s.status)

        val event = s.status match
          case BatteryStatus.Low =>
            Event.Battery.BatteryChargeMediumTariffChanged(s.tariff)
          case BatteryStatus.Medium =>
            Event.Battery.BatteryChargeLowTariffChanged(s.tariff)
          case BatteryStatus.High =>
            Event.Battery.BatteryChargeMediumTariffChanged(s.tariff)

        val (stateAfter, actions) =
          p.process(initialState, event, now)

        val expectedActions = s.status match
          case BatteryStatus.Low =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                s.tariff.label
              )
            )
          case BatteryStatus.Medium =>
            Set[Action](
              Action.SetUIItemValue(config.lowChargeTariffItem, s.tariff.label)
            )
          case BatteryStatus.High =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                s.tariff.label
              )
            )

        assertEquals(actions, expectedActions)

        // Key assertion: should NOT connect regardless of tariff logic
        assertManager(manager, shouldConnect = false)
      }
    }
  }

  // ======================
  // Other important tests
  // ======================

  test("Startup event restores UI state") {
    val manager = new ManagerStub()
    val p = processor(manager)

    val initialState = State()
      .modify(_.battery.status)
      .setTo(Some(BatteryStatus.Low))
      .modify(_.battery.lowChargeTariff)
      .setTo(Some(BatteryChargeTariff.Vall))
      .modify(_.battery.mediumChargeTariff)
      .setTo(Some(BatteryChargeTariff.AllTariffs))

    val (_, actions) = p.process(initialState, Event.System.StartupEvent, now)

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(config.statusItem, "low"),
        Action.SetUIItemValue(config.lowChargeTariffItem, "vall"),
        Action.SetUIItemValue(config.mediumChargeTariffItem, "all tariffs")
      )
    )
  }

  test("Startup event with empty state produces no actions") {
    val manager = new ManagerStub()
    val p = processor(manager)

    val (_, actions) = p.process(State(), Event.System.StartupEvent, now)

    assertEquals(actions, Set.empty)
  }

  test("No grid tariff means no connection") {
    val manager = new ManagerStub()
    val p = processor(manager)

    val state =
      State()
        .modify(_.battery.status)
        .setTo(Some(BatteryStatus.Low))
        .modify(_.battery.lowChargeTariff)
        .setTo(Some(BatteryChargeTariff.AllTariffs))

    val _ = p.process(state, Event.Grid.GridTariffChanged(GridTariff.Pla), now)

    assertManager(manager, shouldConnect = false)
  }
}
