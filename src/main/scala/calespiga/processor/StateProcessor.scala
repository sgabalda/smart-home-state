package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.processor.utils.FilterMqttActionsProcessor
import calespiga.processor.heater.HeaterProcessor
import java.time.ZoneId

trait StateProcessor {
  def process(
      state: State,
      event: Event
  ): (State, Set[Action])
}

object StateProcessor {

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
      offlineDetectorProcessor: SingleProcessor,
      heaterProcessor: SingleProcessor
  ): StateProcessor = Impl(
    List(
      FeatureFlagsProcessor(),
      new FilterMqttActionsProcessor( // filter to be removed when fans are rolled out
        temperatureRelatedProcessor,
        !_.featureFlags.fanManagementEnabled
      ),
      offlineDetectorProcessor,
      new FilterMqttActionsProcessor( // filter to be removed when heater is rolled out
        heaterProcessor,
        !_.featureFlags.heaterManagementEnabled
      )
    )
  )

  def apply(
      config: calespiga.config.ProcessorConfig
  ): StateProcessor =
    this.apply(
      temperatureRelatedProcessor =
        TemperatureRelatedProcessor(config.temperatureRelated),
      offlineDetectorProcessor =
        OfflineDetectorProcessor(config.offlineDetector),
      heaterProcessor = HeaterProcessor(
        config.heater,
        ZoneId.systemDefault(),
        config.offlineDetector,
        config.syncDetector
      )
    )

}
