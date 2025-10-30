package calespiga.processor

import calespiga.model.State
import calespiga.model.Event.EventData
import java.time.Instant
import calespiga.model.Action

trait SingleProcessor { self =>
  def process(
      state: State,
      eventData: EventData,
      timestamp: Instant
  ): (State, Set[Action])

  final def andThen(next: SingleProcessor): SingleProcessor =
    new SingleProcessor {
      def process(
          state: State,
          eventData: EventData,
          timestamp: Instant
      ): (State, Set[Action]) = {
        val (newState, newActions) = self.process(state, eventData, timestamp)
        val (nextState, nextActions) =
          next.process(newState, eventData, timestamp)
        (nextState, newActions ++ nextActions)
      }
    }
}
