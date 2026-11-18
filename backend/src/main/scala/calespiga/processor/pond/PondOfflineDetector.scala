package calespiga.processor.pond

import calespiga.config.OfflineDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.PondCirculation.StatusReported as CirculationStatusReported
import calespiga.model.Event.PondPump.StatusReported
import calespiga.model.OfflineOnlineSignal
import calespiga.model.PondCirculationStatus
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import com.softwaremill.quicklens.*

private[pond] object PondOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case StatusReported(status)
        if status.trim.equalsIgnoreCase("on") ||
          status.trim.equalsIgnoreCase("off") =>
      true
    case CirculationStatusReported(status)
        if PondCirculationStatus.fromString(status).isDefined =>
      true
    case _ => false
  }

  def apply(
      config: OfflineDetectorConfig,
      id: String,
      onlineStatusItem: String
  ): SingleProcessor =
    OfflineDetector(
      config,
      id,
      eventMatcher,
      onlineStatusItem,
      (state, onlineState: OfflineOnlineSignal) =>
        state.modify(_.pondPump.online).setTo(Some(onlineState))
    )
}
