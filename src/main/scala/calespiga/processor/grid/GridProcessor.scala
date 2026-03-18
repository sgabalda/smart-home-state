package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor

/** Aggregate processor for the grid connection relay.
  */
object GridProcessor {

  def apply(
      config: GridConfig,
      syncConfig: SyncDetectorConfig,
      manager: GridConnectionManager
  ): SingleProcessor =
    GridTariffProcessor(config)
      .andThen(GridConnectionProcessor(config, manager))
      .andThen(GridSyncDetector(syncConfig, config.id, config.syncStatusItem))
}
