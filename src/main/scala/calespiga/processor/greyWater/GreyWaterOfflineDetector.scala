package calespiga.processor.greyWater

import calespiga.config.OfflineDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.GreyWater.PumpStatusReported
import calespiga.model.OfflineOnlineSignal
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.OfflineDetector
import com.softwaremill.quicklens.*

private[greyWater] object GreyWaterOfflineDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case PumpStatusReported(_)     => true
    case Event.System.StartupEvent => true
    case _                         => false
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
        state.modify(_.greyWater.online).setTo(Some(onlineState))
    )
}
