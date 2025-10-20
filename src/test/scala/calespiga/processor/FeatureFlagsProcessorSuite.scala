package calespiga.processor

import munit.FunSuite
import calespiga.model.State
import calespiga.model.Event.FeatureFlagEvents.SetFanManagement
import java.time.Instant

class FeatureFlagsProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")

  test("FeatureFlagsProcessor should set fanManagementEnabled to true") {
    val initialState = State()
    val eventData = SetFanManagement(true)
    val processor = FeatureFlagsProcessor()
    val (newState, actions) = processor.process(initialState, eventData, now)
    assertEquals(
      newState.featureFlags.fanManagementEnabled,
      true,
      "fanManagementEnabled should be true"
    )
    assertEquals(actions, Set.empty, "No actions should be produced")
  }

  test("FeatureFlagsProcessor should set fanManagementEnabled to false") {
    val initialState =
      State(featureFlags = State.FeatureFlags(fanManagementEnabled = true))
    val eventData = SetFanManagement(false)
    val processor = FeatureFlagsProcessor()
    val (newState, actions) = processor.process(initialState, eventData, now)
    assertEquals(
      newState.featureFlags.fanManagementEnabled,
      false,
      "fanManagementEnabled should be false"
    )
    assertEquals(actions, Set.empty, "No actions should be produced")
  }
}
