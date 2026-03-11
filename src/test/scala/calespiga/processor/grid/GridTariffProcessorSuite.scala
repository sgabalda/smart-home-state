package calespiga.processor.grid

import munit.FunSuite
import calespiga.model.{Action, Event, GridTariff, State}
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant

class GridTariffProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.gridConfig

  test("GridTariffChanged updates state and sets UI item") {
    val processor = GridTariffProcessor(config)
    val (newState, actions) =
      processor.process(
        State(),
        Event.Grid.GridTariffChanged(GridTariff.Pic),
        now
      )

    assertEquals(newState.grid.currentTariff, Some(GridTariff.Pic))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.tariffItem, "pic"))
    )
  }

  test("GridTariffChanged replaces a previously stored tariff") {
    val processor = GridTariffProcessor(config)
    val state =
      State().modify(_.grid.currentTariff).setTo(Some(GridTariff.Vall))
    val (newState, actions) =
      processor.process(
        state,
        Event.Grid.GridTariffChanged(GridTariff.Pla),
        now
      )

    assertEquals(newState.grid.currentTariff, Some(GridTariff.Pla))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.tariffItem, "pla"))
    )
  }

  test("StartupEvent with no tariff in state produces no actions") {
    val processor = GridTariffProcessor(config)
    val (newState, actions) =
      processor.process(State(), Event.System.StartupEvent, now)

    assertEquals(newState, State())
    assertEquals(actions, Set.empty[Action])
  }

  test("StartupEvent with tariff in state restores UI item") {
    val processor = GridTariffProcessor(config)
    val state =
      State().modify(_.grid.currentTariff).setTo(Some(GridTariff.Vall))
    val (_, actions) = processor.process(state, Event.System.StartupEvent, now)

    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.tariffItem, "vall"))
    )
  }

  test("unrelated event leaves state and actions unchanged") {
    val processor = GridTariffProcessor(config)
    val state = State().modify(_.grid.currentTariff).setTo(Some(GridTariff.Pic))
    val (newState, actions) =
      processor.process(
        state,
        Event.Grid.GridConnectionStatusReported(
          calespiga.model.GridSignal.Connected
        ),
        now
      )

    assertEquals(newState, state)
    assertEquals(actions, Set.empty[Action])
  }
}
