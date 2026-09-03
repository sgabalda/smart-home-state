package calespiga.processor.power

import calespiga.processor.EffectfulProcessor
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
import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object DynamicPowerProcessor {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig,
      manager: GridConnectionManager
  ) extends EffectfulProcessor {

    private def processDynamicPower(
        state: State,
        timestamp: Instant,
        unusedFvPower: Power,
        unusedGridPower: Power
    ): IO[(State, Set[Action], Power)] =
      val orderedConsumers = consumerOrderer.orderConsumers(state, consumers)

      for {
        _ <- logger.info(
          s"Processing dynamic power for consumers in this order: ${orderedConsumers.map(_.uniqueCode).mkString(", ")}"
        )
        dynamicUsedPower = orderedConsumers
          .map(_.currentlyUsedDynamicPower(state, timestamp))
          .fold(Power.zero)(_ + _)

        // as currently the available grid power is fixed and not measured,
        // to be consistent we need to take out the grid power used by the dynamic consumers from the available grid power,
        // otherwise we could be using more power than the available one.
        // when the available grid power is measured, we can remove this and just use the measured available grid power that should eb in the state
        adjustedAvailableGridPower = Power.ofGrid(
          (unusedGridPower - dynamicUsedPower).grid
        )

        totalDynamicPower =
          unusedFvPower + adjustedAvailableGridPower + dynamicUsedPower

        _ <- logger.info(
          s"Total dynamic power available: $totalDynamicPower (unusedFvPower: $unusedFvPower, adjustedAvailableGridPower: $adjustedAvailableGridPower, dynamicUsedPower: $dynamicUsedPower)"
        )

        // we can in the future save the power assigned to each consumer and at the end
        // display it in an UI item or similar

        (finalState, finalActions, remainingPower, totalDynamicPowerUsed) =
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
          }
        _ <- logger.info(
          s"Dynamic power processing completed. Total dynamic power used: $totalDynamicPowerUsed, remaining power: $remainingPower"
        )
      } yield (
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
    ): IO[(State, Set[Action])] = eventData match
      case StartupEvent =>
        val stateWithConsumers = consumerOrderer.addMissingConsumersToState(
          state,
          consumers
        )
        IO.pure(
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

        for {
          _ <- logger.info(
            s"Starting a dynamic power cycle: unusedFvPower: $unusedFvPower, unusedGridPower: $unusedGridPower"
          )
          (stateAfter, actions, totalDynamicUsed) <-
            processDynamicPower(
              state,
              timestamp,
              unusedFvPower,
              unusedGridPower
            )
          _ <- logger.info(s"Current dynamic power usage: $totalDynamicUsed")
          (s, mgrActs) <-
            if (totalDynamicUsed.grid > 0) then
              logger
                .info("Requesting connection to grid")
                .as(
                  manager.requestConnection(GridSignal.DynamicPower, stateAfter)
                )
            else
              logger
                .info("Releasing connection to grid")
                .as(
                  manager.releaseConnection(GridSignal.DynamicPower, stateAfter)
                )
        } yield (s, actions ++ mgrActs)

      case _ =>
        IO.pure((state, Set.empty))

  }

  def apply(
      consumerOrderer: DynamicConsumerOrderer,
      consumers: Set[DynamicPowerConsumer],
      config: DynamicPowerProcessorConfig,
      manager: GridConnectionManager
  ): EffectfulProcessor = Impl(consumerOrderer, consumers, config, manager)

}
