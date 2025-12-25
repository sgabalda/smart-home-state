package calespiga.processor

import calespiga.model.State
import calespiga.model.Event.EventData
import java.time.Instant
import calespiga.model.Action
import cats.effect.IO
import calespiga.processor.power.dynamic.DynamicPowerConsumer

/** A processor that processes events one at a time, modifying the state and
  * producing actions. It may include also a Dynamic Power Consumer, so the
  * logic there can be used dynamically to assign power.
  */
trait SingleProcessor { self =>

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
  ): (State, Set[Action])

  /** The Dynamic Power Consumers associated with this processor, if any. The
    * default is an empty Set, so no dynamic power consumer is associated, but
    * can be overridden by implementations that provide one.
    */
  def dynamicPowerConsumer: Set[DynamicPowerConsumer] = Set.empty

  /** Chains this processor with another one, so that the output state of this 
    * processor is passed as input to the one provided, and
    * actions of this processor ar added to the ones of the next processor.
    *
    * @param next
    * @return
    */
  final def andThen(next: SingleProcessor): SingleProcessor =
    new SingleProcessor {
      override def process(
          state: State,
          eventData: EventData,
          timestamp: Instant
      ): (State, Set[Action]) = {
        val (newState, newActions) = self.process(state, eventData, timestamp)
        val (nextState, nextActions) =
          next.process(newState, eventData, timestamp)
        (nextState, newActions ++ nextActions)
      }

      override def dynamicPowerConsumer: Set[DynamicPowerConsumer] =
        self.dynamicPowerConsumer ++ next.dynamicPowerConsumer
    }

  /** Converts this SingleProcessor into an EffectfulProcessor, for convenience.
    *
    * @return
    */
  final def toEffectful: EffectfulProcessor = new EffectfulProcessor {
    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      IO.pure(self.process(state, eventData, timestamp))
    }

    override def dynamicPowerConsumer: Set[DynamicPowerConsumer] =
      self.dynamicPowerConsumer
  }

  /** Creates a new SingleProcessor with a Dynamic Power Consumer added to this
    * SingleProcessor.
    *
    * @param consumer
    * @return
    */
  final def withDynamicConsumer(
      consumer: DynamicPowerConsumer
  ): SingleProcessor =
    new SingleProcessor {
      override def process(
          state: State,
          eventData: EventData,
          timestamp: Instant
      ): (State, Set[Action]) = self.process(state, eventData, timestamp)

      override def dynamicPowerConsumer: Set[DynamicPowerConsumer] =
        self.dynamicPowerConsumer + consumer
    }

}
