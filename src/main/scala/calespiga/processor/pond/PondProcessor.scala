package calespiga.processor.pond

import calespiga.config.OfflineDetectorConfig
import calespiga.config.PondPumpConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor
import java.time.ZoneId

object PondProcessor {

  def apply(
      config: PondPumpConfig,
      offlineConfig: OfflineDetectorConfig,
      syncConfig: SyncDetectorConfig,
      zone: ZoneId
  ): SingleProcessor =
    PondCirculationControlProcessor(config, zone)
      .andThen(
        PondCirculationSyncDetector(
          syncConfig,
          config.id + "-circulation",
          config.circulationSyncStatusItem
        )
      )
      .andThen(PondPumpControlProcessor(config))
      .andThen(PondMeasurementsProcessor(config))
      .andThen(
        PondOfflineDetector(
          offlineConfig,
          config.id,
          config.controllerOnlineStatusItem
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
