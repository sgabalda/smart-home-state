package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.processor.power.dynamic.Power
import java.time.Instant
import cats.effect.IO

object DynamicPowerConsumerStub {

  def apply(
      code: String = "DynamicPowerConsumerStub",
      currentlyUsedDynamicPowerStub: (State, Instant) => Power = (_, _) =>
        Power.zero,
      usePowerStub: (State, Power, Instant) => DynamicPowerResult =
        (state, _, _) => DynamicPowerResult(state, Set.empty, Power.zero)
  ): DynamicPowerConsumer = new DynamicPowerConsumer {

    override def uniqueCode: String = code

    override def currentlyUsedDynamicPower(
        state: State,
        now: Instant
    ): IO[Power] =
      IO.pure(currentlyUsedDynamicPowerStub(state, now))

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      IO.pure(usePowerStub(state, powerToUse, now))
  }
}
