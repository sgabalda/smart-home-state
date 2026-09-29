package calespiga.processor.greyWater

import calespiga.config.SyncDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.GreyWater.*
import calespiga.model.State
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import java.time.Instant

private[greyWater] object GreyWaterSyncDetector {

  private val eventMatcher: Event.EventData => Boolean = {
    case PumpStatusReported(_) | ModeChanged(_) | StartHourChanged(_) |
        EndHourChanged(_) | ScheduleTransition =>
      true
    case Event.System.StartupEvent => true
    case _                         => false
  }

  def apply(
      config: SyncDetectorConfig,
      id: String,
      syncStatusItem: String
  ): SyncDetector =
    SyncDetector[Option[Boolean]](
      config,
      id,
      _.greyWater.lastCommandSent,
      _.greyWater.pumpOn,
      _.greyWater.lastSyncing,
      (state, lastSyncing: Option[Instant]) =>
        state.modify(_.greyWater.lastSyncing).setTo(lastSyncing),
      syncStatusItem,
      eventMatcher
    )
}
