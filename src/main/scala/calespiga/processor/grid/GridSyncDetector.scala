package calespiga.processor.grid

import calespiga.config.SyncDetectorConfig
import calespiga.model.{Event, State}
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import java.time.Instant

private object GridSyncDetector {

  private def field1ToCheck(state: State) = state.grid.lastCommandSent
  private def field2ToCheck(state: State) = state.grid.status

  private def getLastSyncing(state: State) = state.grid.lastSyncing
  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.grid.lastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.Grid.GridConnectionStatusReported(_) => true
    case Event.Grid.GridManualConnectionChanged(_)  => true
    case Event.System.StartupEvent                  => true
    case _                                          => false
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
