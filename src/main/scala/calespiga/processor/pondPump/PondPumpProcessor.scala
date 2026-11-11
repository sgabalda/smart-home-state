package calespiga.processor.pondPump

import calespiga.config.OfflineDetectorConfig
import calespiga.config.PondPumpConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor
import java.time.ZoneId

object PondPumpProcessor {

  def apply(
      config: PondPumpConfig,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig,
      zone: ZoneId
  ): SingleProcessor =
    PondCirculationControlProcessor(config, zone)
      .andThen(PondPumpControlProcessor(config))
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
