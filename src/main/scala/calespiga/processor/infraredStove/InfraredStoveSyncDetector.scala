package calespiga.processor.heater

import calespiga.processor.utils.SyncDetector
import calespiga.config.SyncDetectorConfig
import calespiga.model.State
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.model.Event
import calespiga.model.Event.Power.PowerProductionReported

private object InfraredStoveSyncDetector {

  private def field1ToCheck(state: State) = state.infraredStove.lastCommandSent

  private def field2ToCheck(state: State) = state.infraredStove.status

  private def getLastSyncing(state: State) = state.infraredStove.lastSyncing

  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.infraredStove.lastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.InfraredStove.InfraredStovePowerStatusReported(_) => true
    case Event.InfraredStove.InfraredStovePowerCommandChanged(_) => true
    case Event.System.StartupEvent                               => true
    case _: PowerProductionReported                              => true
    case _                                                       => false
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
