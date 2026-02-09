package calespiga.processor.heater

import calespiga.model.Event.EventData
import calespiga.model.Event
import calespiga.config.OfflineDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector

private object InfraredStoveOfflineDetector {

  private val eventMatcher: EventData => Boolean = {
    case Event.InfraredStove.InfraredStovePowerStatusReported(_) => true
    case Event.System.StartupEvent                               => true
    case _                                                       => false
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      heaterId: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(offlineConfig, heaterId, eventMatcher, onlineStatusItem)
}
