package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.processor.power.dynamic.Power

object DynamicPowerConsumerStub {

  def apply(
      currentlyUsedDynamicPowerStub: State => Power = _ => Power.zero,
      usePowerStub: (State, Power) => DynamicPowerResult = (state, _) =>
        DynamicPowerResult(state, Set.empty, Power.zero)
  ): DynamicPowerConsumer = new DynamicPowerConsumer {
    override def currentlyUsedDynamicPower(state: State): Power =
      currentlyUsedDynamicPowerStub(state)

    override def usePower(state: State, powerToUse: Power): DynamicPowerResult =
      usePowerStub(state, powerToUse)
  }
}
