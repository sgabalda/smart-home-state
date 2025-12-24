package calespiga.processor

import calespiga.model.State
import calespiga.model.Event.EventData
import java.time.Instant
import calespiga.model.Action
import cats.effect.IO
import calespiga.processor.power.dynamic.DynamicPowerConsumer

/** A processor that processes events one at a time, modifying the state and
  * producing actions, in an effectful way (i.e., returning an IO). If you want
  * a pure processor, use SingleProcessor instead.
  */
trait EffectfulProcessor { self =>

  /** The main processing function, accepts a state and an event, produces a new
    * state and a set of actions. It includes the timestamp of the event being
    * processed.
    *
    * @param state
    * @param eventData
    * @param timestamp
    * @return
    */
  def process(
      state: State,
      eventData: EventData,
      timestamp: Instant
  ): IO[(State, Set[Action])]

  /** The Dynamic Power Consumers associated with this processor, if any. The
    * default is an empty Set, so no dynamic power consumer is associated, but
    * can be overridden by implementations that provide one.
    */
  def dynamicPowerConsumer: Set[DynamicPowerConsumer] = Set.empty

  final def andThen(next: EffectfulProcessor): EffectfulProcessor =
    new EffectfulProcessor {
      override def process(
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
