package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.config.OfflineDetectorConfig

/** Aggregate processor for the grid connection relay.
  */
object GridProcessor {

  def apply(
      config: GridConfig,
      syncConfig: SyncDetectorConfig,
      manager: GridConnectionManager,
      offlineConfig: OfflineDetectorConfig
  ): SingleProcessor =
    GridTariffProcessor(config)
      .andThen(GridConnectionProcessor(config, manager))
      .andThen(GridSyncDetector(syncConfig, config.id, config.syncStatusItem))
      .andThen(
        GridOfflineDetector(offlineConfig, config.id, config.onlineStatusItem)
      )
}
