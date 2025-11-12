package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.processor.SyncDetector
import calespiga.config.SyncDetectorConfig
import calespiga.model.State
import com.softwaremill.quicklens.*
import java.time.Instant
import calespiga.model.Event

private object ElectronicsFanSyncDetector {

  private def field1ToCheck(state: State) =
    state.fans.fanElectronicsLatestCommandSent

  private def field2ToCheck(state: State) = Some(
    state.fans.fanElectronicsStatus
  )

  private def getLastSyncing(state: State) =
    state.fans.fanElectronicsLastSyncing

  private def setLastSyncing(state: State, when: Option[Instant]) =
    state.modify(_.fans.fanElectronicsLastSyncing).setTo(when)

  private val isEventRelevant: Event.EventData => Boolean = {
    case Event.Temperature.Fans.ElectronicsFanCommand(_)     => true
    case Event.Temperature.Fans.ElectronicsFanStatus(_)      => true
    case Event.Temperature.ElectronicsTemperatureMeasured(_) => true
    case Event.Temperature.GoalTemperatureChanged(_)         => true
    case Event.System.StartupEvent                           => true
    case _                                                   => false
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
