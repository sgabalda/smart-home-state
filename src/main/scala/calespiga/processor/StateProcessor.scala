package calespiga.processor

import calespiga.model.{Action, Event, State}
import java.time.Instant
import calespiga.model.Event.EventData

trait StateProcessor {
  def process(
      state: State,
      event: Event
  ): (State, Set[Action])
}

object StateProcessor {

  trait SingleProcessor {
    def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): (State, Set[Action])
  }

  private final case class Impl(
      processors: List[SingleProcessor]
  ) extends StateProcessor {
    override def process(
        state: State,
        event: Event
    ): (State, Set[Action]) = {
      processors.foldLeft((state, Set.empty[Action])) {
        case ((currentState, currentActions), processor) =>
          val (newState, newActions) =
            processor.process(currentState, event.data, event.timestamp)
          (newState, currentActions ++ newActions)
      }
    }
  }

  def apply(
      temperatureRelatedProcessor: SingleProcessor,
      offlineDetectorProcessor: SingleProcessor
  ): StateProcessor = Impl(
    List(temperatureRelatedProcessor, offlineDetectorProcessor)
  )

  def apply(
      config: calespiga.config.ProcessorConfig
  ): StateProcessor =
    this.apply(
      temperatureRelatedProcessor =
        TemperatureRelatedProcessor(config.temperatureRelated),
      offlineDetectorProcessor =
        OfflineDetectorProcessor()
    )

}
