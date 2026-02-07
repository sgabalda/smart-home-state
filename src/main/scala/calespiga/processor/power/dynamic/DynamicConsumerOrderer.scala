package calespiga.processor.power.dynamic

import calespiga.model.State
import com.softwaremill.quicklens.*

trait DynamicConsumerOrderer {

  /** Orders the given consumers according to their priority defined in the
    * state
    *
    * @param state
    * @param consumers
    * @return
    */
  def orderConsumers(
      state: State,
      consumers: Set[DynamicPowerConsumer]
  ): Seq[DynamicPowerConsumer]

  def addMissingConsumersToState(
      state: State,
      consumers: Set[DynamicPowerConsumer]
  ): State

}

object DynamicConsumerOrderer {
  private final case class Impl() extends DynamicConsumerOrderer {

    // Add the missing consumers to the ordered list of priority in the state
    override def addMissingConsumersToState(
        state: State,
        consumers: Set[DynamicPowerConsumer]
    ): State = {
      val codesInState = state.powerManagement.dynamic.consumersOrder.toSet
      val codesOfConsumers = consumers.map(_.uniqueCode)

      val consumersToAdd = codesOfConsumers.filterNot(codesInState.contains)
      val consumersToRemove = codesInState.filterNot(codesOfConsumers.contains)

      state
        .modify(_.powerManagement.dynamic.consumersOrder)
        .setTo(
          state.powerManagement.dynamic.consumersOrder.filterNot { code =>
            consumersToRemove.contains(code)
          }.distinct ++ // distinct to avoid duplicates in case of corrupt state loaded
            consumersToAdd.toSeq
              .sortBy(
                identity
              ) // sort to have a deterministic order for the new consumers
        )
    }

    override def orderConsumers(
        state: calespiga.model.State,
        consumers: Set[DynamicPowerConsumer]
    ): Seq[DynamicPowerConsumer] = {
      val consumerMap = consumers.map { consumer =>
        consumer.uniqueCode -> consumer
      }.toMap
      state.powerManagement.dynamic.consumersOrder.flatMap { code =>
        consumerMap.get(code)
      }

    }
  }

  def apply(): DynamicConsumerOrderer = Impl()
}
