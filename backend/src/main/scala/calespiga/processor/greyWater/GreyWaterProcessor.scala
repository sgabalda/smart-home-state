package calespiga.processor.greyWater

import calespiga.config.GreyWaterConfig
import calespiga.config.OfflineDetectorConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor
import java.time.ZoneId

object GreyWaterProcessor {

  def apply(
      config: GreyWaterConfig,
      zone: ZoneId,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor =
    GreyWaterControlProcessor(config, zone)
      .andThen(
        GreyWaterOfflineDetector(
          offlineConfig,
          config.id,
          config.onlineStatusItem
        )
      )
      .andThen(
        GreyWaterSyncDetector(
          syncConfig,
          config.id,
          config.syncStatusItem
        )
      )
}
