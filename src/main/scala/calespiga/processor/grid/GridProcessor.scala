package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.config.SyncDetectorConfig
import calespiga.processor.SingleProcessor

/** Aggregate processor for the grid connection relay.
  */
object GridProcessor {

  def apply(
      config: GridConfig,
      syncConfig: SyncDetectorConfig
  ): SingleProcessor =
    GridConnectionProcessor(config, GridConnectionManager(config))
      .andThen(GridSyncDetector(syncConfig, config.id, config.syncStatusItem))
      .andThen(GridTariffProcessor(config))
}
