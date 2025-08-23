package calespiga.processor

import calespiga.model.Fixture
import munit.CatsEffectSuite
import calespiga.processor.StateProcessor.SingleProcessor

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
      val sut = StateProcessor(temperatureRelatedProcessor)
      assertEquals(
        sut.process(Fixture.state, event),
        (Fixture.state, Set.empty)
      )
      assertEquals(
        executed,
        true
      )
    }
  }
}
