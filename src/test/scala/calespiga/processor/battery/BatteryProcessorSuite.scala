package calespiga.processor.battery

import munit.FunSuite
import calespiga.model.{
  Action,
  Event,
  State,
  GridTariff,
  BatteryStatus,
  BatteryChargeTariff,
  GridSignal
}
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.processor.grid.GridConnectionManager

class BatteryProcessorSuite extends FunSuite {
  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.batteryConfig

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

  private def baseProcessor(manager: ManagerStub) =
    BatteryChargeProcessor(
      config,
      manager
    )

  test("Battery status reported updates state and UI") {
    val manager = new ManagerStub()
    val processor = baseProcessor(manager)
    val (newState, actions) = processor.process(
      State(),
      Event.Battery.BatteryStatusReported(BatteryStatus.Low),
      now
    )
    assertEquals(newState.battery.status, Some(BatteryStatus.Low))
    assert(actions.contains(Action.SetUIItemValue(config.statusItem, "low")))
  }

  test("Battery low charge tariff change updates state and UI") {
    val manager = new ManagerStub()
    val processor = baseProcessor(manager)
    val (newState, actions) = processor.process(
      State(),
      Event.Battery.BatteryChargeLowTariffChanged(BatteryChargeTariff.Vall),
      now
    )
    assertEquals(
      newState.battery.lowChargeTariff,
      Some(BatteryChargeTariff.Vall)
    )
    assert(
      actions.contains(
        Action.SetUIItemValue(config.lowChargeTariffItem, "vall")
      )
    )
  }

  test("Battery medium charge tariff change updates state and UI") {
    val manager = new ManagerStub()
    val processor = baseProcessor(manager)
    val (newState, actions) = processor.process(
      State(),
      Event.Battery.BatteryChargeMediumTariffChanged(
        BatteryChargeTariff.PlaAndVall
      ),
      now
    )
    assertEquals(
      newState.battery.mediumChargeTariff,
      Some(BatteryChargeTariff.PlaAndVall)
    )
    assert(
      actions.contains(
        Action.SetUIItemValue(config.mediumChargeTariffItem, "pla + vall")
      )
    )
  }

  val always = (_: GridTariff) => true
  val never = (_: GridTariff) => false
  val withVall = (t: GridTariff) => t == GridTariff.Vall
  val withPlaAndVall = (t: GridTariff) =>
    t == GridTariff.Vall || t == GridTariff.Pla

  val tariffs = GridTariff.values

  val statusAndTariffs =
    List[(BatteryStatus, BatteryChargeTariff, GridTariff => Boolean)](
      (BatteryStatus.Low, BatteryChargeTariff.AllTariffs, always),
      (BatteryStatus.Low, BatteryChargeTariff.PlaAndVall, withPlaAndVall),
      (BatteryStatus.Low, BatteryChargeTariff.Vall, withVall),
      (BatteryStatus.Low, BatteryChargeTariff.NoneCharge, never),
      (BatteryStatus.Medium, BatteryChargeTariff.AllTariffs, always),
      (BatteryStatus.Medium, BatteryChargeTariff.PlaAndVall, withPlaAndVall),
      (BatteryStatus.Medium, BatteryChargeTariff.Vall, withVall),
      (BatteryStatus.Medium, BatteryChargeTariff.NoneCharge, never),
      (BatteryStatus.High, BatteryChargeTariff.AllTariffs, never),
      (BatteryStatus.High, BatteryChargeTariff.PlaAndVall, never),
      (BatteryStatus.High, BatteryChargeTariff.Vall, never),
      (BatteryStatus.High, BatteryChargeTariff.NoneCharge, never)
    )

  tariffs.foreach { (toTariff) =>
    statusAndTariffs.foreach { (status, chargeTariff, result) =>
      val connect = if result(toTariff) then "connect" else "not connect"
      test(
        s"Tariff change to $toTariff, battery=$status, charging=$chargeTariff => $connect"
      ) {
        val manager = new ManagerStub()
        val processor = baseProcessor(manager)

        val stateWithTariffAndStatus = State()
          .modify(_.grid.currentTariff)
          .setTo(Some(toTariff))
          .modify(_.battery.status)
          .setTo(Some(status))

        val initialState = status match {
          case BatteryStatus.Low =>
            stateWithTariffAndStatus
              .modify(_.battery.lowChargeTariff)
              .setTo(Some(chargeTariff))
              .modify(_.battery.mediumChargeTariff)
              .setTo(None)
          case BatteryStatus.Medium =>
            stateWithTariffAndStatus
              .modify(_.battery.lowChargeTariff)
              .setTo(None)
              .modify(_.battery.mediumChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.High =>
            stateWithTariffAndStatus
              .modify(_.battery.lowChargeTariff)
              .setTo(None)
              .modify(_.battery.mediumChargeTariff)
              .setTo(None)
        }

        val (stateAfter, actions) = processor.process(
          initialState,
          Event.Grid.GridTariffChanged(toTariff),
          now
        )

        assert(
          actions.isEmpty,
          "No UI actions should be produced on tariff change"
        )

        if result(toTariff) then
          assert(
            manager.requestCalls.contains(
              GridSignal.Batteries
            ) && manager.releaseCalls.isEmpty
          )
        else
          assert(
            manager.releaseCalls.contains(
              GridSignal.Batteries
            ) && manager.requestCalls.isEmpty
          )
      }
    }
  }

  tariffs.foreach { (toTariff) =>
    statusAndTariffs.foreach { (status, chargeTariff, result) =>
      val connect = if result(toTariff) then "connect" else "not connect"
      test(
        s"Tariff=$toTariff, battery to $status, charging=$chargeTariff => $connect"
      ) {
        val manager = new ManagerStub()
        val processor = baseProcessor(manager)

        val stateWithTariff = State()
          .modify(_.grid.currentTariff)
          .setTo(Some(toTariff))
          .modify(_.battery.status)
          .setTo(None)

        val initialState = status match {
          case BatteryStatus.Low =>
            stateWithTariff
              .modify(_.battery.lowChargeTariff)
              .setTo(Some(chargeTariff))
              .modify(_.battery.mediumChargeTariff)
              .setTo(None)
          case BatteryStatus.Medium =>
            stateWithTariff
              .modify(_.battery.lowChargeTariff)
              .setTo(None)
              .modify(_.battery.mediumChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.High =>
            stateWithTariff
              .modify(_.battery.lowChargeTariff)
              .setTo(None)
              .modify(_.battery.mediumChargeTariff)
              .setTo(None)
        }

        val (stateAfter, actions) = processor.process(
          initialState,
          Event.Battery.BatteryStatusReported(status),
          now
        )

        assertEquals(
          actions,
          Set[Action](Action.SetUIItemValue(config.statusItem, status.label)),
          "No UI actions should be produced on tariff change"
        )

        assertEquals(stateAfter.battery.status, Some(status))

        if result(toTariff) then
          assert(
            manager.requestCalls.contains(
              GridSignal.Batteries
            ) && manager.releaseCalls.isEmpty
          )
        else
          assert(
            manager.releaseCalls.contains(
              GridSignal.Batteries
            ) && manager.requestCalls.isEmpty
          )
      }
    }
  }

  tariffs.foreach { (toTariff) =>
    statusAndTariffs.foreach { (status, chargeTariff, result) =>
      val connect = if result(toTariff) then "connect" else "not connect"
      test(
        s"Tariff=$toTariff, battery=$status, charging for $status to $chargeTariff => $connect"
      ) {
        val manager = new ManagerStub()
        val processor = baseProcessor(manager)

        val initialState = State()
          .modify(_.grid.currentTariff)
          .setTo(Some(toTariff))
          .modify(_.battery.status)
          .setTo(Some(status))

        val eventForCurrent = status match {
          case BatteryStatus.Low =>
            Event.Battery.BatteryChargeLowTariffChanged(chargeTariff)
          case BatteryStatus.Medium =>
            Event.Battery.BatteryChargeMediumTariffChanged(chargeTariff)
          case BatteryStatus.High =>
            // For high status, charge tariff doesn't matter, but we can still test the change
            Event.Battery.BatteryChargeLowTariffChanged(chargeTariff)
        }

        val (stateAfter, actions) = processor.process(
          initialState,
          eventForCurrent,
          now
        )

        val actionsExpected = status match {
          case BatteryStatus.Low =>
            Set[Action](
              Action.SetUIItemValue(
                config.lowChargeTariffItem,
                chargeTariff.label
              )
            )
          case BatteryStatus.Medium =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                chargeTariff.label
              )
            )
          case BatteryStatus.High =>
            Set[Action](
              Action.SetUIItemValue(
                config.lowChargeTariffItem,
                chargeTariff.label
              )
            )
        }

        val stateExpected = status match {
          case BatteryStatus.Low =>
            initialState
              .modify(_.battery.lowChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.Medium =>
            initialState
              .modify(_.battery.mediumChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.High =>
            initialState
              .modify(_.battery.lowChargeTariff)
              .setTo(Some(chargeTariff))
        }

        assertEquals(
          actions,
          actionsExpected,
          "UI actions should update the correct tariff item"
        )

        assertEquals(
          stateAfter,
          stateExpected,
          "State should update the correct tariff field"
        )

        if result(toTariff) then
          assert(
            manager.requestCalls.contains(
              GridSignal.Batteries
            ) && manager.releaseCalls.isEmpty
          )
        else
          assert(
            manager.releaseCalls.contains(
              GridSignal.Batteries
            ) && manager.requestCalls.isEmpty
          )
      }
    }
  }

  tariffs.foreach { (toTariff) =>
    statusAndTariffs.foreach { (status, chargeTariff, _) =>
      val result = never
      val connect = "not connect"
      test(
        s"Tariff=$toTariff, battery=$status, charging for $status to $chargeTariff => $connect"
      ) {
        val manager = new ManagerStub()
        val processor = baseProcessor(manager)

        val initialState = State()
          .modify(_.grid.currentTariff)
          .setTo(Some(toTariff))
          .modify(_.battery.status)
          .setTo(Some(status))

        val eventForOther = status match {
          case BatteryStatus.Low =>
            Event.Battery.BatteryChargeMediumTariffChanged(chargeTariff)
          case BatteryStatus.Medium =>
            Event.Battery.BatteryChargeLowTariffChanged(chargeTariff)
          case BatteryStatus.High =>
            // For high status, charge tariff doesn't matter, but we can still test the change
            Event.Battery.BatteryChargeMediumTariffChanged(chargeTariff)
        }

        val (stateAfter, actions) = processor.process(
          initialState,
          eventForOther,
          now
        )

        val actionsExpected = status match {
          case BatteryStatus.Low =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                chargeTariff.label
              )
            )
          case BatteryStatus.Medium =>
            Set[Action](
              Action.SetUIItemValue(
                config.lowChargeTariffItem,
                chargeTariff.label
              )
            )
          case BatteryStatus.High =>
            Set[Action](
              Action.SetUIItemValue(
                config.mediumChargeTariffItem,
                chargeTariff.label
              )
            )
        }

        val stateExpected = status match {
          case BatteryStatus.Low =>
            initialState
              .modify(_.battery.mediumChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.Medium =>
            initialState
              .modify(_.battery.lowChargeTariff)
              .setTo(Some(chargeTariff))
          case BatteryStatus.High =>
            initialState
              .modify(_.battery.mediumChargeTariff)
              .setTo(Some(chargeTariff))
        }

        assertEquals(
          actions,
          actionsExpected,
          "UI actions should update the correct tariff item"
        )

        assertEquals(
          stateAfter,
          stateExpected,
          "State should update the correct tariff field"
        )

        if result(toTariff) then
          assert(
            manager.requestCalls.contains(
              GridSignal.Batteries
            ) && manager.releaseCalls.isEmpty
          )
        else
          assert(
            manager.releaseCalls.contains(
              GridSignal.Batteries
            ) && manager.requestCalls.isEmpty
          )
      }
    }
  }
}
