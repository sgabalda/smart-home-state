package calespiga.processor.infraredStove

import calespiga.config.InfraredStoveConfig
import calespiga.config.OfflineDetectorConfig
import java.time.ZoneId
import calespiga.processor.SingleProcessor
import calespiga.config.SyncDetectorConfig

object InfraredStoveProcessor {

  def apply(
      config: InfraredStoveConfig,
      zone: ZoneId,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor = {
    val syncDetector =
      InfraredStoveSyncDetector(
        syncConfig,
        config.id,
        config.syncStatusItem
      )

    InfraredStovePowerProcessor(config = config, zone = zone)
      .andThen(
        InfraredStoveOfflineDetector(
          offlineConfig,
          config.id,
          config.onlineStatusItem
        )
      )
      .andThen(InfraredStoveManualTimeProcessor(config))
      .andThen(syncDetector)
      .withDynamicConsumer(
        InfraredStoveDynamicPowerConsumer(config, syncDetector)
      )
  }

}
