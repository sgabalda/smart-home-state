package calespiga.processor.carCharger

import calespiga.config.{CarChargerConfig, OfflineDetectorConfig}
import calespiga.model.Event
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import com.softwaremill.quicklens.*

private[carCharger] object CarChargerOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case Event.CarCharger.CarChargerStatusReported(_) => true
    case Event.CarCharger.CarChargerPowerReported(_)  => true
    case _                                            => false
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      config: CarChargerConfig
  ): SingleProcessor =
    OfflineDetector(
      offlineConfig,
      config.id,
      eventMatcher,
      config.onlineStatusItem,
      (state, online) => state.modify(_.carCharger.online).setTo(Some(online)),
      Some(config.offlineNotification)
    )
}
