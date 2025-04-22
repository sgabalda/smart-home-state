package calespiga.processor

import calespiga.model.{Action, Event, State}

trait StateProcessor {
  def process(
      state: State,
      event: Event
  ): (State, Set[Action])
}

object StateProcessor {

  private final case class Impl(
      TemperatureRelatedProcessor: TemperatureRelatedProcessor
  ) extends StateProcessor {
    override def process(
        state: State,
        event: Event
    ): (State, Set[Action]) = {
      event match {
        case Event.Temperature(timestamp, temperature) =>
          TemperatureRelatedProcessor.process(state, temperature)
      }
    }
  }

  def apply(temperatureRelatedProcessor: TemperatureRelatedProcessor = TemperatureRelatedProcessor()): StateProcessor = Impl(
    temperatureRelatedProcessor
  )

}
