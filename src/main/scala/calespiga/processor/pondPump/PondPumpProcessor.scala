package calespiga.processor.pondPump

import calespiga.config.OfflineDetectorConfig
import calespiga.config.PondPumpConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor

object PondPumpProcessor {

  def apply(
      config: PondPumpConfig,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor =
    PondPumpControlProcessor(config)
      .andThen(PondMeasurementsProcessor(config))
      .andThen(
        PondPumpOfflineDetector(
          offlineConfig,
          config.id,
          config.onlineStatusItem
        )
      )
      .andThen(
        PondPumpSyncDetector(
          syncConfig,
          config.id,
          config.syncStatusItem
        )
      )
}
