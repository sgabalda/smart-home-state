package calespiga.processor.battery

import calespiga.config.BatteryConfig
import calespiga.processor.SingleProcessor

/** Aggregate processor for battery status and alerts.
  */
object BatteryProcessor {

  def apply(
      config: BatteryConfig,
      offlineConfig: calespiga.config.OfflineDetectorConfig
  ): SingleProcessor =
    BatteryStatusProcessor(config)
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
