package calespiga.processor

import calespiga.model.Fixture
import munit.CatsEffectSuite
import calespiga.model.{State, Action}

class StateProcessorSuite extends CatsEffectSuite {

  test(
    "Events of type TemperatureRelated are forwarded to the provided TemperatureRelatedProcessor"
  ) {
    Fixture.allEvents.foreach { event =>
      var executed = false
      val temperatureRelatedProcessor: SingleProcessor =
        (state, _, _) => {
          executed = true
          (state, Set.empty)
        }
      val offlineDetectorProcessor: SingleProcessor =
        (state, _, _) => (state, Set.empty)
      val heaterProcessor: SingleProcessor =
        (state, _, _) => (state, Set.empty)

      val sut =
        StateProcessor(
          temperatureRelatedProcessor,
          offlineDetectorProcessor,
          heaterProcessor
        )
      assertEquals(
        sut.process(Fixture.state, event),
        (Fixture.state, Set.empty),
        s"Processing event: $event"
      )
      assertEquals(
        executed,
        true,
        s"TemperatureRelatedProcessor should be executed for event: $event"
      )
    }
  }

  test(
    "StateProcessor.apply wraps temperatureRelatedProcessor with filterMqttActionsProcessor based on fanManagementEnabled"
  ) {
    // Dummy processor that always emits a dummy Action
    val dummyAction = Action.SendMqttStringMessage("topic", "payload")
    val dummyProcessor: SingleProcessor =
      (state, _, _) => (state, Set(dummyAction))

    val dummyOffline: SingleProcessor =
      (state, _, _) => (state, Set.empty)

    val heaterProcessor: SingleProcessor =
      (state, _, _) => (state, Set.empty)

    val processor =
      StateProcessor(dummyProcessor, dummyOffline, heaterProcessor)

    val event = Fixture.event

    // fanManagementEnabled = false: actions should be filtered (empty)
    val stateWithFlagFalse =
      State(featureFlags = State.FeatureFlags(fanManagementEnabled = false))
    val (_, actionsFiltered) = processor.process(stateWithFlagFalse, event)
    assert(
      actionsFiltered.isEmpty,
      "Actions should be filtered when fanManagementEnabled is false"
    )

    // fanManagementEnabled = true: actions should pass through
    val stateWithFlagTrue =
      State(featureFlags = State.FeatureFlags(fanManagementEnabled = true))
    val (_, actionsPassed) = processor.process(stateWithFlagTrue, event)
    assert(
      actionsPassed.contains(dummyAction),
      "Actions should pass when fanManagementEnabled is true"
    )
  }

}
