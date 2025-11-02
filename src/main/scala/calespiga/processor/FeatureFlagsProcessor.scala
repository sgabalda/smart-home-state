package calespiga.processor

import calespiga.model.{Event, State, Action}
import calespiga.model.Event.EventData
import java.time.Instant
import com.softwaremill.quicklens.*

object FeatureFlagsProcessor {
  class Impl extends SingleProcessor {

    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case Event.FeatureFlagEvents.SetFanManagement(enable) =>
        (
          state.modify(_.featureFlags.fanManagementEnabled).setTo(enable),
          Set.empty
        )
      case Event.FeatureFlagEvents.SetHeaterManagement(enable) =>
        (
          state.modify(_.featureFlags.heaterManagementEnabled).setTo(enable),
          Set.empty
        )
      case _ =>
        (state, Set.empty)
    }
  }

  def apply(): SingleProcessor = new Impl
}
