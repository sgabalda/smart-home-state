package calespiga.processor.temperatures

import calespiga.config.TemperatureFansConfig
import calespiga.processor.SingleProcessor

object TemperaturesProcessor {
  def apply(
      config: TemperatureFansConfig,
      offlineConfig: calespiga.config.OfflineDetectorConfig
  ): SingleProcessor = {
    TemperaturesUpdater(config.temperaturesItems)
      .andThen(
        BatteryFanManager(config.fansConfig.batteryFan)
      )
      .andThen(
        ElectronicsFanManager(config.fansConfig.electronicsFan)
      )
      .andThen(
        TemperaturesOfflineDetector(
          offlineConfig,
          config.id,
          config.onlineStatusItem
        )
      )
  }
}
