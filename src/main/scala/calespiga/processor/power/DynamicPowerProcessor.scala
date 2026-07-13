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

    private def processDynamicPower(
        state: State,
        timestamp: Instant,
        unusedFvPower: Power,
        unusedGridPower: Power
    ): (State, Set[Action]) =
      val orderedConsumers = consumerOrderer.orderConsumers(state, consumers)

      val dynamicUsedPower = orderedConsumers
        .map(_.currentlyUsedDynamicPower(state, timestamp))
        .fold(Power.zero)(_ + _)

      // as currently the available grid power is fixed and not measured,
      // to be consistent we need to take out the grid power used by the dynamic consumers from the available grid power,
      // otherwise we could be using more power than the available one.
      // when the available grid power is measured, we can remove this and just use the measured available grid power that should eb in the state
      val adjustedAvailableGridPower =
        Power.ofGrid((unusedGridPower - dynamicUsedPower).grid)

      val totalDynamicPower = unusedFvPower + adjustedAvailableGridPower +
        dynamicUsedPower

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
        val unusedFvPower = Power.ofFv(powerDiscarded)
        val unusedGridPower = state.grid.availablePower
          .map(Power.ofGrid)
          .getOrElse(Power.zero)

        processDynamicPower(state, timestamp, unusedFvPower, unusedGridPower)

      case _ =>
        (state, Set.empty)

  }

  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig
  ): SingleProcessor = Impl(consumerOrderer, consumers, config)

}
