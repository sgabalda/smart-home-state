package calespiga.processor

import calespiga.model.{Action, Event, State}
import java.time.Instant

trait StateProcessor {
  def process(
      state: State,
      event: Event,
      timestamp: Instant
  ): (State, Set[Action])
}

object StateProcessor {

  private final case class Impl(
      TemperatureRelatedProcessor: TemperatureRelatedProcessor
  ) extends StateProcessor {
    override def process(
        state: State,
        event: Event,
        timestamp: Instant
    ): (State, Set[Action]) = {
      val (newState, actions) = event match {
        case Event(timestamp, temperature: Event.Temperature.TemperatureData) =>
          TemperatureRelatedProcessor.process(state, temperature, timestamp)
      }

      // TODO get the diff of both states, and add the actions for the changes if they are to be reported

      (newState, actions)
    }
  }

  def apply(
      temperatureRelatedProcessor: TemperatureRelatedProcessor =
        TemperatureRelatedProcessor()
  ): StateProcessor = Impl(
    temperatureRelatedProcessor
  )

}
