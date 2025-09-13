package calespiga.processor

import munit.FunSuite
import calespiga.model.{Action, Event, State}
import java.time.Instant

class OfflineDetectorProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val emptyState = calespiga.model.Fixture.state

  test("OfflineDetectorProcessor should process any event without changing state") {
    val sut = OfflineDetectorProcessor()
    
    // Test with a simple event
    val eventData = Event.Temperature.BatteryTemperatureMeasured(25.0)
    
    val (resultState, resultActions) = sut.process(
      emptyState,
      eventData,
      now
    )
    
    assertEquals(
      resultState,
      emptyState,
      "State should remain unchanged"
    )
    assertEquals(
      resultActions,
      Set.empty[Action],
      "No actions should be produced"
    )
  }

}
