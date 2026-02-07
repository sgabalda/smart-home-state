package calespiga.processor.power.dynamic

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Event
import java.time.Instant
import calespiga.model.Action
import com.softwaremill.quicklens.*

object DynamicPowerPriorityProcessor {

  private final case class Impl() extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case e: Event.Power.DynamicPower.DynamicPowerConsumerPriorityChanged =>
        val changedConsumerCode = e.consumerUniqueCode
        val currentPriority = state.powerManagement.dynamic.consumersOrder
        val currentIndex = currentPriority.indexWhere {
          case consumer if consumer == changedConsumerCode => true
          case _                                           => false
        }
        // Priority is 1-based, convert to 0-based index
        val targetIndex = e.priority - 1

        if (
          currentIndex == -1 ||
          targetIndex < 0 ||
          targetIndex >= currentPriority.size
        ) {
          // consumer not found or invalid priority index, do nothing
          (state, Set.empty)
        } else if (targetIndex == currentIndex) {
          // same index, do nothing
          (state, Set.empty)
        } else {
          val itemToReplace = currentPriority(targetIndex)
          val resultingPriority = currentPriority.toVector
            .updated(
              targetIndex,
              changedConsumerCode
            )
            .updated(currentIndex, itemToReplace)

          (
            state
              .modify(_.powerManagement.dynamic.consumersOrder)
              .setTo(resultingPriority),
            // update the items showing current priority for each consumer
            Set(
              Action.SetUIItemValue(
                item = itemToReplace,
                value = (currentIndex + 1).toString
              )
            )
          )
        }

      case _ =>
        (state, Set.empty)
  }

  def apply(): SingleProcessor = Impl()
}
