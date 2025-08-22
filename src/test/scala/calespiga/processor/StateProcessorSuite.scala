package calespiga.processor

import calespiga.model.Fixture
import munit.CatsEffectSuite
import java.time.Instant

class StateProcessorSuite extends CatsEffectSuite {

  private val now = Instant.now()

  test(
    "Events of type TemperatureRelated are forwarded to the provided TemperatureRelatedProcessor"
  ) {
    Fixture.allTemperatureRelatedEvents.foreach { event =>
      var executed = false
      val temperatureRelatedProcessor: TemperatureRelatedProcessor =
        (state, _, _) => {
          executed = true
          (state, Set.empty)
        }
      val sut = StateProcessor(temperatureRelatedProcessor)
      assertEquals(
        sut.process(Fixture.state, event, now),
        (Fixture.state, Set.empty)
      )
      assertEquals(
        executed,
        true
      )
    }
  }
}
