package calespiga.processor

import calespiga.model.{Action, Event, State}
import java.time.Instant
import calespiga.model.Event.EventData
import calespiga.processor.utils.FilterMqttActionsProcessor

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

    def andThen(next: SingleProcessor): SingleProcessor = new SingleProcessor {
      def process(
          state: State,
          eventData: EventData,
          timestamp: Instant
      ): (State, Set[Action]) = {
        val (newState, newActions) = this.process(state, eventData, timestamp)
        val (nextState, nextActions) =
          next.process(newState, eventData, timestamp)
        (nextState, newActions ++ nextActions)
      }
    }
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
    List(
      FeatureFlagsProcessor(),
      new FilterMqttActionsProcessor( // filter to be removed when fans are rolled out
        temperatureRelatedProcessor,
        !_.featureFlags.fanManagementEnabled
      ),
      offlineDetectorProcessor
    )
  )

  def apply(
      config: calespiga.config.ProcessorConfig
  ): StateProcessor =
    this.apply(
      temperatureRelatedProcessor =
        TemperatureRelatedProcessor(config.temperatureRelated),
      offlineDetectorProcessor =
        OfflineDetectorProcessor(config.offlineDetector)
    )

}
