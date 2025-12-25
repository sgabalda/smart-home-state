package calespiga.processor.heater

import calespiga.config.HeaterConfig
import calespiga.config.OfflineDetectorConfig
import java.time.ZoneId
import calespiga.processor.SingleProcessor
import calespiga.config.SyncDetectorConfig

object HeaterProcessor {

  def apply(
      heaterConfig: HeaterConfig,
      zone: ZoneId,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor = {
    val syncDetector =
      HeaterSyncDetector(
        syncConfig,
        heaterConfig.id,
        heaterConfig.syncStatusItem
      )

    HeaterPowerProcessor(config = heaterConfig, zone = zone)
      .andThen(
        HeaterOfflineDetector(
          offlineConfig,
          heaterConfig.id,
          heaterConfig.onlineStatusItem
        )
      )
      .andThen(
        syncDetector
      )
      .withDynamicConsumer(
        HeaterDynamicPowerConsumer(heaterConfig, syncDetector)
      )
  }

}
