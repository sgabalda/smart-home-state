package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.processor.SyncDetector
import calespiga.config.SyncDetectorConfig
import calespiga.model.State
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.model.Event

private object BatteryFanSyncDetector {

  private def field1ToCheck(state: State) =
    state.fans.fanBatteriesLatestCommandSent

  private def field2ToCheck(state: State) = Some(state.fans.fanBatteriesStatus)

  private def getLastSyncing(state: State) = state.fans.fanBatteriesLastSyncing

  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.fans.fanBatteriesLastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.Temperature.Fans.BatteryFanCommand(_) => true
    case Event.Temperature.Fans.BatteryFanStatus(_)  => true
    case Event.System.StartupEvent                   => true
    case _                                           => false
  }

  def apply(
      syncConfig: SyncDetectorConfig,
      id: String,
      statusItem: String
  ): SingleProcessor =
    SyncDetector(
      syncConfig,
      id,
      field1ToCheck,
      field2ToCheck,
      getLastSyncing,
      setLastSyncing,
      statusItem,
      isEventRelevant
    )

}
