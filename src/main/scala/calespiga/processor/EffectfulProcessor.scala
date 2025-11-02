package calespiga.processor

import calespiga.model.State
import calespiga.model.Event.EventData
import java.time.Instant
import calespiga.model.Action
import cats.effect.IO

trait EffectfulProcessor { self =>
  def process(
      state: State,
      eventData: EventData,
      timestamp: Instant
  ): IO[(State, Set[Action])]

  final def andThen(next: EffectfulProcessor): EffectfulProcessor =
    new EffectfulProcessor {
      def process(
          state: State,
          eventData: EventData,
          timestamp: Instant
      ): IO[(State, Set[Action])] = {
        for {
          (newState, newActions) <- self.process(state, eventData, timestamp)
          (nextState, nextActions) <- next.process(
            newState,
            eventData,
            timestamp
          )
        } yield (nextState, newActions ++ nextActions)
      }
    }
}
