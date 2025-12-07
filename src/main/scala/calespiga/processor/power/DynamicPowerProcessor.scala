package calespiga.processor.power

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Event
import calespiga.model.Event.Power.PowerProductionReported
import java.time.Instant
import calespiga.model.Action
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer

object DynamicPowerProcessor {

  private final case class Impl(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer]
  ) extends SingleProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case PowerProductionReported(_, _, powerDiscarded, _) =>
        val orderedConsumers = consumerOrderer.orderConsumers(state, consumers)

        val totalDynamicPower = powerDiscarded + orderedConsumers
          .map(_.currentlyUsedDynamicPower(state))
          .sum

        orderedConsumers.foldLeft(
          (state, Set.empty[Action], totalDynamicPower)
        ) { case ((currentState, currentActions, remainingPower), consumer) =>
          if (remainingPower <= 0f) {
            (currentState, currentActions, 0f)
          } else {
            val result = consumer.usePower(currentState, remainingPower)
            (
              result.newState,
              currentActions ++ result.actions,
              remainingPower - result.powerUsed
            )
          }
        } match {
          case (finalState, finalActions, _) =>
            (finalState, finalActions)
        }
      case _ =>
        (state, Set.empty)

  }

  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer]
  ): SingleProcessor = Impl(consumerOrderer, consumers)

}
