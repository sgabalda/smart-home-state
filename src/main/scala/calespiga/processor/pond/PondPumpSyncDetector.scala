package calespiga.processor.pond

import calespiga.config.SyncDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.PondPump.StatusReported
import calespiga.model.Event.Pump.CommandRequested
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import java.time.Instant

private[pond] object PondPumpSyncDetector {

  def apply(
      config: SyncDetectorConfig,
      id: String,
      syncStatusItem: String
  ): SingleProcessor = {
    val eventMatcher: Event.EventData => Boolean = {
      case CommandRequested(pumpId, _, _) if pumpId == id => true
      case StatusReported(status)
          if status.trim.equalsIgnoreCase("on") ||
            status.trim.equalsIgnoreCase("off") =>
        true
      case Event.System.StartupEvent => true
      case _                         => false
    }

    SyncDetector[Option[Boolean]](
      config,
      id,
      _.pondPump.lastCommandSent,
      _.pondPump.status,
      _.pondPump.lastSyncing,
      (state, lastSyncing: Option[Instant]) =>
        state.modify(_.pondPump.lastSyncing).setTo(lastSyncing),
      syncStatusItem,
      eventMatcher
    )
  }
}
