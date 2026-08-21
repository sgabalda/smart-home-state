package calespiga.processor.power

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Event
import calespiga.model.Event.Power.PowerStatusReported
import java.time.Instant
import calespiga.model.Action
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.Power
import calespiga.config.DynamicPowerProcessorConfig
import calespiga.processor.grid.GridConnectionManager
import calespiga.model.GridSignal
import calespiga.model.Event.System.StartupEvent

object DynamicPowerProcessor {

  private object NoopGridConnectionManager
      extends calespiga.processor.grid.GridConnectionManager {
    override def requestConnection(
        actor: calespiga.model.GridSignal.ActorsConnecting,
        state: State
    ) = (state, Set.empty)
    override def releaseConnection(
        actor: calespiga.model.GridSignal.ActorsConnecting,
        state: State
    ) = (state, Set.empty)
    override def applyConnection(state: State) = (state, Set.empty)
  }

  private final case class Impl(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig,
      manager: GridConnectionManager
  ) extends SingleProcessor {

    private def processDynamicPower(
        state: State,
        timestamp: Instant,
        unusedFvPower: Power,
        unusedGridPower: Power
    ): (State, Set[Action], Power) =
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

      val foldResult = orderedConsumers.foldLeft(
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
      }

      val (finalState, finalActions, _, totalDynamicPowerUsed) = foldResult

      (
        finalState,
        finalActions + Action.SetUIItemValue(
          config.dynamicFVPowerUsedItem,
          totalDynamicPowerUsed.fv.toString
        ),
        totalDynamicPowerUsed
      )

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

      case PowerStatusReported(production, gridConsumption) =>
        val unusedFvPower =
          production.map(_.powerDiscarded).map(Power.ofFv).getOrElse(Power.zero)
        val unusedGridPower = state.grid.availablePower
          .map(
            _ - gridConsumption.powerConsumed
          ) // remove, of all grid available power, the power consumed from grid
          .map(Power.ofGrid)
          .getOrElse(Power.zero)

        val (stateAfter, actions, totalDynamicUsed) =
          processDynamicPower(state, timestamp, unusedFvPower, unusedGridPower)

        if (totalDynamicUsed.grid > 0) then
          val (s, mgrActs) =
            manager.requestConnection(GridSignal.DynamicPower, stateAfter)
          (s, actions ++ mgrActs)
        else
          val (s, mgrActs) =
            manager.releaseConnection(GridSignal.DynamicPower, stateAfter)
          (s, actions ++ mgrActs)

      case _ =>
        (state, Set.empty)

  }

  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig,
      manager: GridConnectionManager
  ): SingleProcessor = Impl(consumerOrderer, consumers, config, manager)

  // Backwards-compatible overload used in tests and simple instantiations
  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig
  ): SingleProcessor =
    Impl(consumerOrderer, consumers, config, NoopGridConnectionManager)

}
