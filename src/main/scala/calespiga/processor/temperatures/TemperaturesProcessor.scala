package calespiga.processor.temperatures

import calespiga.config.TemperatureFansConfig
import calespiga.processor.SingleProcessor
import calespiga.config.SyncDetectorConfig

object TemperaturesProcessor {
  def apply(
      config: TemperatureFansConfig,
      offlineConfig: calespiga.config.OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor = {
    TemperaturesUpdater(config.temperaturesItems)
      .andThen(
        BatteryFanManager(config.fansConfig.batteryFan).andThen(
          BatteryFanSyncDetector(
            syncConfig,
            config.fansConfig.batteryFan.batteryFanId,
            config.fansConfig.batteryFan.batteryFanInconsistencyItem
          )
        )
      )
      .andThen(
        ElectronicsFanManager(config.fansConfig.electronicsFan).andThen(
          ElectronicsFanSyncDetector(
            syncConfig,
            config.fansConfig.electronicsFan.electronicsFanId,
            config.fansConfig.electronicsFan.electronicsFanInconsistencyItem
          )
        )
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
