package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.Action
import calespiga.processor.power.dynamic.DynamicPowerConsumer.Power

trait DynamicPowerConsumer {
  def currentlyUsedDynamicPower(state: State): Power
  def usePower(state: State, powerToUse: Power): DynamicPowerResult
}

object DynamicPowerConsumer {
  // if there are more types (e.g. grid), they can be added here
  case class Power(unusedFV: Float) {
    def +(other: Power): Power = Power(this.unusedFV + other.unusedFV)
    def -(other: Power): Power = Power(this.unusedFV - other.unusedFV)
    def <=(other: Power): Boolean = this.unusedFV <= other.unusedFV
  }

  val zeroPower: Power = Power(0f)

  case class DynamicPowerResult(
      state: State,
      actions: Set[Action],
      powerUsed: Power
  )
}
