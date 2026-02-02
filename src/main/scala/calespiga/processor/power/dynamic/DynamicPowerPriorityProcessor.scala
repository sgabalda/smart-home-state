package calespiga.processor.power.dynamic

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Event
import java.time.Instant
import calespiga.model.Action
import calespiga.processor.heater.HeaterDynamicPowerConsumer
import com.softwaremill.quicklens.*

object DynamicPowerPriorityProcessor {

  private final case class Impl() extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case e: Event.Power.DynamicPower.DynamicPowerData =>
        e match {
          case Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority) =>
            val currentPriority = state.powerManagement.dynamic.consumersOrder
            val currentIndex = currentPriority.indexWhere {
              case consumer
                  if consumer == HeaterDynamicPowerConsumer.consumerUniqueCode =>
                true
              case _ => false
            }
            // Priority is 1-based, convert to 0-based index
            val targetIndex = priority - 1

            val resultingPriority =
              if (
                currentIndex == -1 ||
                targetIndex < 0 ||
                targetIndex >= currentPriority.size
              ) {
                // consumer not found or invalid priority index, do nothing
                currentPriority
              } else {
                val itemToReplace = currentPriority(targetIndex)
                currentPriority.toVector
                  .updated(
                    targetIndex,
                    HeaterDynamicPowerConsumer.consumerUniqueCode
                  )
                  .updated(currentIndex, itemToReplace)
              }

            (
              state
                .modify(_.powerManagement.dynamic.consumersOrder)
                .setTo(resultingPriority),
              Set.empty
            )
        }
      case _ =>
        (state, Set.empty)
  }

  def apply(): SingleProcessor = Impl()
}
