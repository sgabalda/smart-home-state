package calespiga.processor

import calespiga.model.{Action, Event, State}
import java.time.Instant

object OfflineDetectorProcessor {

  private final case class Impl(
      // Configuration will be added later
  ) extends StateProcessor.SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      // TODO: Implement offline detection logic
      eventData match {
        case _ =>
          // For now, just return the state unchanged with no actions
          (state, Set.empty[Action])
      }
    }
  }

  def apply(): StateProcessor.SingleProcessor = Impl()

}
