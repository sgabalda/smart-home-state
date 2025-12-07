package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult

object DynamicPowerConsumerStub {

  def apply(
      currentlyUsedDynamicPowerStub: State => Float = _ => 0.0f,
      usePowerStub: (State, Float) => DynamicPowerResult = (state, _) =>
        DynamicPowerResult(state, Set.empty, 0.0f)
  ): DynamicPowerConsumer = new DynamicPowerConsumer {
    override def currentlyUsedDynamicPower(state: State): Float =
      currentlyUsedDynamicPowerStub(state)

    override def usePower(state: State, powerToUse: Float): DynamicPowerResult =
      usePowerStub(state, powerToUse)
  }
}
