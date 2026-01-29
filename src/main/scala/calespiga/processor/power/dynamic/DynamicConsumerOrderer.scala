package calespiga.processor.power.dynamic

trait DynamicConsumerOrderer {
  /**
    * Orders the given consumers according to their priority defined in the state
    *
    * @param state
    * @param consumers
    * @return
    */
  def orderConsumers(
      state: calespiga.model.State,
      consumers: Set[DynamicPowerConsumer]
  ): Seq[DynamicPowerConsumer]
}

object DynamicConsumerOrderer {
  private final case class Impl() extends DynamicConsumerOrderer {

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
