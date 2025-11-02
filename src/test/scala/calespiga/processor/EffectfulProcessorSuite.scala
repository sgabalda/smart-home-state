package calespiga.processor

import munit.CatsEffectSuite
import cats.effect.IO
import calespiga.model.{State, Action, Event}
import java.time.Instant

class EffectfulProcessorSuite extends CatsEffectSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val event = Event.Temperature.BatteryTemperatureMeasured(42.0)

  // Dummy effectful processors
  class AddFlagProcessor(flagValue: Boolean, action: Action)
      extends EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      val newState = state.copy(featureFlags =
        state.featureFlags.copy(fanManagementEnabled = flagValue)
      )
      IO.pure((newState, Set(action)))
    }
  }

  class SetHeaterStatusProcessor(
      status: Option[calespiga.model.HeaterSignal.ControllerState],
      action: Action
  ) extends EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      val newState = state.copy(heater = state.heater.copy(status = status))
      IO.pure((newState, Set(action)))
    }
  }

  test("andThen returns state and actions in order (effectful)") {
    val initialState = State()
    val action1 = Action.SetOpenHabItemValue("item1", "value1")
    val action2 = Action.SetOpenHabItemValue("item2", "value2")
    val processor1 = new AddFlagProcessor(true, action1)
    val processor2 = new SetHeaterStatusProcessor(
      Some(calespiga.model.HeaterSignal.Power500),
      action2
    )
    val composed = processor1.andThen(processor2)

    composed.process(initialState, event, now).map {
      case (finalState, actions) =>
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
}
