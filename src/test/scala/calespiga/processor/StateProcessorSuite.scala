package calespiga.processor

import munit.CatsEffectSuite
import cats.effect.IO
import com.softwaremill.quicklens.*

class StateProcessorSuite extends CatsEffectSuite {
  import calespiga.model.{State, Action, Event}
  import java.time.Instant

  val now = Instant.parse("2023-08-17T10:00:00Z")
  val event = Event.Temperature.BatteryTemperatureMeasured(42.0)

  val initialState =
    State().modify(_.temperatures.batteriesTemperature).setTo(Some(0d))
  val action1 = Action.SetUIItemValue("item1", "value1")
  val action2 = Action.SetUIItemValue("item2", "value2")
  // Dummy processors
  val processor1 = new EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      if (eventData != event) fail("Event not correct")
      val newState =
        state.modify(_.temperatures.batteriesTemperature).setTo(Some(10d))
      IO.pure((newState, Set(action1)))
    }
  }
  val processor2 = new EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      if (eventData != event) fail("Event not correct")
      val newState =
        state.modify(_.temperatures.batteriesTemperature).using(_.map(_ + 5))
      IO.pure((newState, Set(action2)))
    }
  }

  test("Events are forwarded to all the processors") {

    val stateProcessor = StateProcessor(processor1, processor2)

    stateProcessor.process(initialState, Event(now, event)).map {
      case (_, actions) =>
        // Both processors should have received the event and produced their actions
        assertEquals(actions, Set[Action](action1, action2))
    }
  }

  test("the State is modified by all the processors, in the given order ") {
    val stateProcessor = StateProcessor(processor1, processor2)

    stateProcessor.process(initialState, Event(now, event)).map {
      case (finalState, actions) =>
        // State should reflect both changes, in order
        assertEquals(finalState.temperatures.batteriesTemperature, Some(15d))
    }
  }
}
