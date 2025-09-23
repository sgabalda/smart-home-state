package calespiga.processor

import calespiga.model.{Event, State, Action}
import calespiga.model.Event.EventData
import java.time.Instant

object FeatureFlagsProcessor {
  class Impl extends StateProcessor.SingleProcessor {

    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case Event.FeatureFlagEvents.SetFanManagement(enable) =>
        (
          state.copy(
            featureFlags =
              state.featureFlags.copy(fanManagementEnabled = enable),
            temperatures = state.temperatures,
            fans = state.fans
          ),
          Set.empty
        )
      case _ =>
        (state, Set.empty)
    }
  }

  def apply(): StateProcessor.SingleProcessor = new Impl
}
