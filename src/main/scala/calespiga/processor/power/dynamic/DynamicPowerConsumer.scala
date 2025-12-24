package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.Action
import calespiga.processor.power.dynamic.Power
import java.time.Instant

trait DynamicPowerConsumer {
  def currentlyUsedDynamicPower(state: State, now: Instant): Power
  def usePower(state: State, powerToUse: Power): DynamicPowerResult
}

object DynamicPowerConsumer {

  case class DynamicPowerResult(
      state: State,
      actions: Set[Action],
      powerUsed: Power
  )
}
