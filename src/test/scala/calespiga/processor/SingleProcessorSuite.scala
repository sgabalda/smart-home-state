package calespiga.processor

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import java.time.Instant

class SingleProcessorSuite extends FunSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val dummyEvent = Event.Temperature.BatteryTemperatureMeasured(0.0)

  // Dummy processors for testing
  class AddFieldProcessor(action: Action) extends SingleProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      val newState = state.copy(featureFlags =
        state.featureFlags.copy(fanManagementEnabled = true)
      ) // just to mutate something
      (newState, Set(action))
    }
  }

  class SetHeaterStatusProcessor(
      status: Option[calespiga.model.HeaterSignal.ControllerState],
      action: Action
  ) extends SingleProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      val newState = state.copy(heater = state.heater.copy(status = status))
      (newState, Set(action))
    }
  }

  test("andThen returns state and actions in order") {
    val initialState = State()
    val action1 = Action.SetOpenHabItemValue("item1", "value1")
    val action2 = Action.SetOpenHabItemValue("item2", "value2")
    val processor1 = new AddFieldProcessor(action1)
    val processor2 = new SetHeaterStatusProcessor(
      Some(calespiga.model.HeaterSignal.Power500),
      action2
    )
    val composed = processor1.andThen(processor2)

    val (finalState, actions) = composed.process(initialState, dummyEvent, now)

    // Check that the state is the one from processor2, applied after processor1
    assertEquals(
      finalState.heater.status,
      Some(calespiga.model.HeaterSignal.Power500)
    )
    assertEquals(finalState.featureFlags.fanManagementEnabled, true)
    // Check that both actions are present
    assertEquals(actions, Set[Action](action1, action2))
  }
}
