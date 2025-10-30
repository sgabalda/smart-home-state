package calespiga.processor.utils

import calespiga.model.{Action, State}
import calespiga.processor.SingleProcessor
import java.time.Instant
import calespiga.model.Event.EventData

/** A SingleProcessor that wraps another SingleProcessor and conditionally
  * filters out all actions of type SendMqttStringMessage based on a predicate
  * function applied to the resulting State.
  *
  * @param nested
  *   the processor to wrap
  * @param shouldFilter
  *   a function that takes the resulting State and returns true if MQTT actions
  *   should be filtered
  */
class FilterMqttActionsProcessor(
    nested: SingleProcessor,
    shouldFilter: State => Boolean
) extends SingleProcessor {
  override def process(
      state: State,
      eventData: EventData,
      timestamp: Instant
  ): (State, Set[Action]) = {
    val (newState, actions) = nested.process(state, eventData, timestamp)
    if (shouldFilter(newState)) {
      val filtered = actions.filterNot {
        case Action.SendMqttStringMessage(_, _) => true
        case _                                  => false
      }
      (newState, filtered)
    } else {
      (newState, actions)
    }
  }
}
