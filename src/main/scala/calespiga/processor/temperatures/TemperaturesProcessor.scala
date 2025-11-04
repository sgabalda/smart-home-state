package calespiga.processor.temperatures

import calespiga.config.TemperatureRelatedConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.TemperatureRelatedProcessor

object TemperaturesProcessor {
  def apply(
      temperatureRelatedConfig: TemperatureRelatedConfig,
      offlineConfig: calespiga.config.OfflineDetectorConfig
  ): SingleProcessor = {
    TemperatureRelatedProcessor(temperatureRelatedConfig).andThen(
      TemperaturesOfflineDetector(
        offlineConfig,
        temperatureRelatedConfig.id,
        temperatureRelatedConfig.onlineStatusItem
      )
    )
  }
}
