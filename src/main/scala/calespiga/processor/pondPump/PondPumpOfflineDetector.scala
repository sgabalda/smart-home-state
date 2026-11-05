package calespiga.processor.pondPump

import calespiga.config.OfflineDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.PondPump.StatusReported
import calespiga.model.OfflineOnlineSignal
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import com.softwaremill.quicklens.*

private[pondPump] object PondPumpOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case StatusReported(status)
        if status.trim.equalsIgnoreCase("on") ||
          status.trim.equalsIgnoreCase("off") =>
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
