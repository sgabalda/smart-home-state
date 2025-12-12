package calespiga.processor.power.dynamic

trait DynamicConsumerOrderer {
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
      // TODO: implement actual ordering logic
      consumers.toSeq
    }
  }

  def apply(): DynamicConsumerOrderer = Impl()
}
