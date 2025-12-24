package calespiga.processor.temperatures

import calespiga.model.Event
import calespiga.config.OfflineDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector

private object TemperaturesOfflineDetector {

  private def eventMatcher(event: Event.EventData): Boolean = {
    event match {
      case Event.Temperature.BatteryTemperatureMeasured(_)       => true
      case Event.Temperature.ElectronicsTemperatureMeasured(_)   => true
      case Event.Temperature.ExternalTemperatureMeasured(_)      => true
      case Event.Temperature.BatteryClosetTemperatureMeasured(_) => true
      case Event.Temperature.Fans.BatteryFanStatus(_)            => true
      case Event.Temperature.Fans.ElectronicsFanStatus(_)        => true
      case _                                                     => false
    }
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      heaterId: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(offlineConfig, heaterId, eventMatcher, onlineStatusItem)
}
