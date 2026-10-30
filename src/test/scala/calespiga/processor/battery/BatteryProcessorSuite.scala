package calespiga.processor.battery

import calespiga.model.Action
import calespiga.model.BatteryStatus
import calespiga.model.Event
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import java.time.Instant
import munit.FunSuite

class BatteryProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val config = ProcessorConfigHelper.batteryConfig
  private val processor = BatteryStatusProcessor(config)

  test("Battery status reported updates state and UI") {
    val (newState, actions) = processor.process(
      State(),
      Event.Battery.BatteryStatusReported(BatteryStatus.Low),
      now
    )

    assertEquals(newState.battery.status, Some(BatteryStatus.Low))
    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "low"))
    )
  }

  test("Startup event restores battery status UI") {
    val state =
      State(battery = State.Battery(status = Some(BatteryStatus.Medium)))
    val (_, actions) = processor.process(state, Event.System.StartupEvent, now)

    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(config.statusItem, "medium"))
    )
  }

  test("Startup event with no battery status produces no actions") {
    val (_, actions) =
      processor.process(State(), Event.System.StartupEvent, now)

    assertEquals(actions, Set.empty)
  }
}
