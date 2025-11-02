package calespiga.processor.heater

import calespiga.model.Event.EventData
import calespiga.model.Event
import calespiga.config.OfflineDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.OfflineDetector

private object HeaterOfflineDetector {

  private val eventMatcher: EventData => Boolean = {
    case Event.Heater.HeaterIsHotReported(_)       => true
    case Event.Heater.HeaterPowerStatusReported(_) => true
    case Event.System.StartupEvent                 => true
    case _                                         => false
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      heaterId: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(offlineConfig, heaterId, eventMatcher, onlineStatusItem)
}
