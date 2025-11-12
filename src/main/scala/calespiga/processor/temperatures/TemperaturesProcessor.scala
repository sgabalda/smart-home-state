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
        BatteryFanManager(config.fans.batteryFan).andThen(
          BatteryFanSyncDetector(
            syncConfig,
            config.fans.batteryFan.batteryFanId,
            config.fans.batteryFan.batteryFanInconsistencyItem
          )
        )
      )
      .andThen(
        ElectronicsFanManager(config.fans.electronicsFan).andThen(
          ElectronicsFanSyncDetector(
            syncConfig,
            config.fans.electronicsFan.electronicsFanId,
            config.fans.electronicsFan.electronicsFanInconsistencyItem
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
