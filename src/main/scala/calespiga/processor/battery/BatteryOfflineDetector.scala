package calespiga.processor.battery

import calespiga.config.OfflineDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import calespiga.model.Event

private[battery] object BatteryOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case Event.Battery.BatteryStatusReported(_) => true
    case Event.System.StartupEvent              => true
    case _                                      => false
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      batteryId: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(offlineConfig, batteryId, eventMatcher, onlineStatusItem)
}
