package calespiga.processor.carCharger

import calespiga.config.{CarChargerConfig, OfflineDetectorConfig}
import calespiga.processor.SingleProcessor
import java.time.ZoneId

/** Aggregate processor for the car charger device.
  */
object CarChargerProcessor {

  def apply(
      config: CarChargerConfig,
      zone: ZoneId,
      offlineDetectorConfig: OfflineDetectorConfig,
      syncConfig: calespiga.config.SyncDetectorConfig
  ): SingleProcessor =
    val carChargerSyncDetector =
      CarChargerSyncDetector(syncConfig, config.id, config.syncStatusItem)

    CarChargerOfflineDetector(offlineDetectorConfig, config)
      .andThen(
        CarChargerStatusProcessor(config)
      )
      .andThen(
        CarChargerPowerProcessor(config)
      )
      .andThen(
        carChargerSyncDetector
      )
      .withDynamicConsumer(
        CarChargerDynamicPowerConsumer(config, carChargerSyncDetector)
      )
      .andThen(
        CarChargerEnergyProcessor(config, zone)
      )
}
