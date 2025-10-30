package calespiga.processor.utils

import munit.FunSuite
import calespiga.model.{Action, State}
import calespiga.model.Event.EventData
import calespiga.processor.SingleProcessor
import java.time.Instant

class FilterMqttActionsProcessorSuite extends FunSuite {

  // Dummy processor that always returns the given actions
  class DummyProcessor(actionsToReturn: Set[Action]) extends SingleProcessor {
    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      (state, actionsToReturn)
  }

  val dummyState = State()
  val dummyEventData = null.asInstanceOf[EventData] // not used
  val dummyTimestamp = Instant.now()

  val mqttAction = Action.SendMqttStringMessage("topic", "payload")
  val otherAction = Action.SetOpenHabItemValue("item", "value")

  test("filters MQTT actions if predicate returns true, keeps others") {
    val processor = new FilterMqttActionsProcessor(
      new DummyProcessor(Set(mqttAction, otherAction)),
      _ => true
    )
    val (_, actions) =
      processor.process(dummyState, dummyEventData, dummyTimestamp)
    assert(!actions.contains(mqttAction), "MQTT action should be filtered")
    assert(
      actions.contains(otherAction),
      "Other actions should not be filtered"
    )
  }

  test("does not filter MQTT actions if predicate returns false") {
    val processor = new FilterMqttActionsProcessor(
      new DummyProcessor(Set(mqttAction, otherAction)),
      _ => false
    )
    val (_, actions) =
      processor.process(dummyState, dummyEventData, dummyTimestamp)
    assert(actions.contains(mqttAction), "MQTT action should not be filtered")
    assert(
      actions.contains(otherAction),
      "Other actions should not be filtered"
    )
  }

  test("does not filter non-MQTT actions regardless of predicate") {
    val processor = new FilterMqttActionsProcessor(
      new DummyProcessor(Set(otherAction)),
      _ => true
    )
    val (_, actions) =
      processor.process(dummyState, dummyEventData, dummyTimestamp)
    assert(
      actions.contains(otherAction),
      "Non-MQTT actions should never be filtered"
    )
  }
}
