package calespiga.processor.grid

import munit.FunSuite
import calespiga.model.{Action, Event, GridTariff, State}
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant

class GridAvailablePowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.gridConfig
  private val processor = GridAvailablePowerProcessor(config)

  test("GridTariffChanged sets the configured available power") {
    val existingState =
      State().modify(_.grid.availablePower).setTo(Some(123.4f))

    val (newState, actions) = processor.process(
      existingState,
      Event.Grid.GridTariffChanged(GridTariff.Pic),
      now
    )

    assertEquals(newState.grid.availablePower, Some(config.availablePower))
    assertEquals(actions, Set.empty[Action])
  }

  test("unrelated events leave the state unchanged") {
    val existingState =
      State().modify(_.grid.availablePower).setTo(Some(123.4f))

    val (newState, actions) = processor.process(
      existingState,
      Event.System.StartupEvent,
      now
    )

    assertEquals(newState, existingState)
    assertEquals(actions, Set.empty[Action])
  }
}
