package calespiga.processor.power.dynamic

import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.Action
import calespiga.processor.power.dynamic.Power
import java.time.Instant

trait DynamicPowerConsumer {

  /** A unique code to identify this consumer among all the others. Has to be
    * different for each implementation. The current implementation uses the OH
    * item associated to the priority as specified in the configuration.
    *
    * @return
    *   the unique code for this consumer
    */
  def uniqueCode: String

  /** Returns the amount of dynamic power currently used by this consumer
    *
    * @param state
    *   the current state
    * @param now
    *   the current timestamp
    * @return
    *   the amount of dynamic power currently used
    */
  def currentlyUsedDynamicPower(state: State, now: Instant): Power

  /** Applies the given amount of dynamic power for this consumer
    *
    * @param state
    *   the current state
    * @param powerToUse
    *   the amount of power to use
    * @param now
    *   the current timestamp
    * @return
    *   the result of the operation, including the new state, actions to
    *   perform, and the actual power used
    */
  def usePower(
      state: State,
      powerToUse: Power,
      now: Instant
  ): DynamicPowerResult
}

object DynamicPowerConsumer {

  case class DynamicPowerResult(
      state: State,
      actions: Set[Action],
      powerUsed: Power
  )
}
