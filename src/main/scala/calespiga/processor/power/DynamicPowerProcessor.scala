package calespiga.processor.power

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Event
import calespiga.model.Event.Power.PowerProductionReported
import java.time.Instant
import calespiga.model.Action
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.Power
import calespiga.config.DynamicPowerProcessorConfig
import calespiga.model.Event.System.StartupEvent

object DynamicPowerProcessor {

  private final case class Impl(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case StartupEvent =>
        val stateWithConsumers = consumerOrderer.addMissingConsumersToState(
          state,
          consumers
        )
        (
          stateWithConsumers,
          Set(Action.SetUIItemValue(config.dynamicFVPowerUsedItem, "0")) ++
            stateWithConsumers.powerManagement.dynamic.consumersOrder.zipWithIndex
              .map { case (consumerCode, index) =>
                Action.SetUIItemValue(
                  item = consumerCode,
                  value = (index + 1).toString
                )
              }
        )

      case PowerProductionReported(_, _, powerDiscarded, _) =>
        val orderedConsumers = consumerOrderer.orderConsumers(state, consumers)

        val initialPower = Power.ofFv(powerDiscarded)

        val totalDynamicPower = initialPower + orderedConsumers
          .map(_.currentlyUsedDynamicPower(state, timestamp))
          .fold(Power.zero)(_ + _)

        // we can in the future save the power assigned to each consumer and at the end
        // display it in an UI item or similar

        orderedConsumers.foldLeft(
          (state, Set.empty[Action], totalDynamicPower, Power.zero)
        ) {
          case (
                (
                  currentState,
                  currentActions,
                  remainingPower,
                  currentPowerUsed
                ),
                consumer
              ) =>
            if (remainingPower <= Power.zero) {
              (currentState, currentActions, Power.zero, currentPowerUsed)
            } else {
              val result =
                consumer.usePower(currentState, remainingPower, timestamp)
              (
                result.state,
                currentActions ++ result.actions,
                remainingPower - result.powerUsed,
                currentPowerUsed + result.powerUsed
              )
            }
        } match {
          case (finalState, finalActions, _, totalDynamicPowerUsed) =>
            (
              finalState,
              finalActions + Action.SetUIItemValue(
                config.dynamicFVPowerUsedItem,
                totalDynamicPowerUsed.fv.toString
              )
            )
        }
      case _ =>
        (state, Set.empty)

  }

  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig
  ): SingleProcessor = Impl(consumerOrderer, consumers, config)

}
