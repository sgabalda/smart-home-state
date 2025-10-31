package calespiga.processor.heater

import calespiga.processor.SingleProcessor
import calespiga.processor.SyncDetector
import calespiga.config.SyncDetectorConfig
import calespiga.model.State
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.model.Event

private object HeaterSyncDetector {

  private def field1ToCheck(state: State) = state.heater.lastCommandSent

  private def field2ToCheck(state: State) = state.heater.status

  private def getLastSyncing(state: State) = state.heater.lastSyncing

  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.heater.lastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.Heater.HeaterPowerCommandChanged(_) => true
    case Event.Heater.HeaterPowerStatusReported(_) => true
    case Event.System.StartupEvent                 => true
    case _                                         => false
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
