package calespiga.processor.battery

import calespiga.config.BatteryConfig
import calespiga.processor.SingleProcessor

/** Aggregate processor for the grid connection relay.
  */
object BatteryProcessor {

  def apply(
      config: BatteryConfig,
      manager: calespiga.processor.grid.GridConnectionManager,
      offlineConfig: calespiga.config.OfflineDetectorConfig
  ): SingleProcessor =
    BatteryChargeProcessor(config, manager)
      .andThen(
        BatteryOfflineDetector(
          offlineConfig,
          config.id,
          config.onlineStatusItem,
          config.offlineNotification
        )
      )
      .andThen(
        BatteryAlertOnLowProcessor()
      )
}
