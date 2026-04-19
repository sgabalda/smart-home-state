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
      offlineDetectorConfig: OfflineDetectorConfig
  ): SingleProcessor =
    CarChargerOfflineDetector(offlineDetectorConfig, config)
      .andThen(
        CarChargerStatusProcessor(config)
      )
      .andThen(
        CarChargerEnergyProcessor(config, zone)
      )
}
