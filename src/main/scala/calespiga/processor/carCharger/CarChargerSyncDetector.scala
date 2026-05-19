package calespiga.processor.carCharger

import calespiga.processor.utils.SyncDetector
import calespiga.config.SyncDetectorConfig
import calespiga.model.State
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.model.Event

private object CarChargerSyncDetector {

  private def field1ToCheck(state: State) = state.carCharger.lastCommandSent

  private def field2ToCheck(state: State) = state.carCharger.switchStatus

  private def getLastSyncing(state: State) = state.carCharger.lastSyncing

  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.carCharger.lastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.CarCharger.CarChargerPowerCommandChanged(_) => true
    case Event.CarCharger.CarChargerStatusReported(_)      => true
    case Event.CarCharger.CarChargerPowerReported(_)       => true
    case Event.System.StartupEvent                         => true
    case _: Event.Power.PowerProductionReported            => true
    case _                                                 => false
  }

  def apply(
      syncConfig: SyncDetectorConfig,
      id: String,
      statusItem: String
  ): SyncDetector =
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
