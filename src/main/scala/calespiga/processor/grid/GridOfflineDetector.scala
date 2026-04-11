package calespiga.processor.grid

import calespiga.config.OfflineDetectorConfig
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import calespiga.model.Event
import com.softwaremill.quicklens.*

private[grid] object GridOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case Event.Grid.GridConnectionStatusReported(_) => true
    case Event.System.StartupEvent                  => true
    case _                                          => false
  }

  def apply(
      offlineConfig: OfflineDetectorConfig,
      batteryId: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(
      offlineConfig,
      batteryId,
      eventMatcher,
      onlineStatusItem,
      (state, online) => state.modify(_.grid.online).setTo(Some(online))
    )
}
