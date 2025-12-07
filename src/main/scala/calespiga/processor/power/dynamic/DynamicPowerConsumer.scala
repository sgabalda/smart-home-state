package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.Action

trait DynamicPowerConsumer {
  def currentlyUsedDynamicPower(state: State): Float
  def usePower(state: State, powerToUse: Float): DynamicPowerResult
}

object DynamicPowerConsumer {
  case class DynamicPowerResult(
      newState: State,
      actions: Set[Action],
      powerUsed: Float
  )
}
