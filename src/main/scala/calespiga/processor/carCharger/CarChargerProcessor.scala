package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.processor.SingleProcessor
import java.time.ZoneId

/** Aggregate processor for the car charger device.
  */
object CarChargerProcessor {

  def apply(config: CarChargerConfig, zone: ZoneId): SingleProcessor =
    CarChargerStatusProcessor(config)
      .andThen(
        CarChargerEnergyProcessor(config, zone)
      )
}
