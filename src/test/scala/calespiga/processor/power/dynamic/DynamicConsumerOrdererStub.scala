package calespiga.processor.power.dynamic

import calespiga.model.State

object DynamicConsumerOrdererStub {

  def apply(
      orderConsumersStub: (State, Set[DynamicPowerConsumer]) => Seq[
        DynamicPowerConsumer
      ] = (_, consumers) => consumers.toSeq
  ): DynamicConsumerOrderer = new DynamicConsumerOrderer {
    override def orderConsumers(
        state: State,
        consumers: Set[DynamicPowerConsumer]
    ): Seq[DynamicPowerConsumer] =
      orderConsumersStub(state, consumers)
  }
}
