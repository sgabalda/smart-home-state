package calespiga.processor

import calespiga.model.Fixture
import munit.CatsEffectSuite

class StateProcessorSuite extends CatsEffectSuite {

  test(
    "Events of type TemperatureRelated are forwarded to the provided TemperatureRelatedProcessor"
  ) {
    Fixture.allTemperatureRelatedEvents.foreach { event =>
      var executed = false
      val temperatureRelatedProcessor: TemperatureRelatedProcessor =
        (state, _) => {
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
