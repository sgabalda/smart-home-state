package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.processor.power.dynamic.Power
import java.time.Instant

object DynamicPowerConsumerStub {

  def apply(
      currentlyUsedDynamicPowerStub: (State, Instant) => Power = (_, _) =>
        Power.zero,
      usePowerStub: (State, Power, Instant) => DynamicPowerResult =
        (state, _, _) => DynamicPowerResult(state, Set.empty, Power.zero)
  ): DynamicPowerConsumer = new DynamicPowerConsumer {

    override def uniqueCode: String = "DynamicPowerConsumerStub"

    override def currentlyUsedDynamicPower(state: State, now: Instant): Power =
      currentlyUsedDynamicPowerStub(state, now)

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): DynamicPowerResult =
      usePowerStub(state, powerToUse, now)
  }
}
