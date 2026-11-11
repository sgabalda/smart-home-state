package calespiga.processor.pond

import calespiga.config.SyncDetectorConfig
import calespiga.model.Event
import calespiga.model.Event.PondCirculation.*
import calespiga.model.PondCirculationCommand
import calespiga.model.PondCirculationCommand.Stop
import calespiga.model.PondCirculationStatus
import calespiga.model.PondCirculationStatus.Depuradora as StatusDepuradora
import calespiga.model.PondCirculationStatus.Diposit as StatusDiposit
import calespiga.model.PondCirculationStatus.Off as StatusOff
import calespiga.model.PondCirculationStatus.PreparingDepuradora
import calespiga.model.PondCirculationStatus.PreparingDiposit
import calespiga.model.PondCirculationStatus.PreparingOff
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import java.time.Instant

private[pond] object PondCirculationSyncDetector {

  private def reportedCommand(
      state: State
  ): Option[PondCirculationCommand] =
    state.pondCirculation.status.flatMap {
      case StatusDepuradora => Some(PondCirculationCommand.Depuradora)
      case StatusDiposit    => Some(PondCirculationCommand.Diposit)
      case StatusOff        => Some(Stop)
      case PreparingDiposit | PreparingDepuradora | PreparingOff => None
    }

  private val eventMatcher: Event.EventData => Boolean = {
    case StatusReported(status) =>
      PondCirculationStatus.fromString(status).isDefined
    case ModeChanged(_) | StartHourChanged(_) | EndHourChanged(_) |
        CadenceChanged(_) | ScheduleTransition =>
      true
    case Event.System.StartupEvent => true
    case _                         => false
  }

  def apply(
      config: SyncDetectorConfig,
      id: String,
      syncStatusItem: String
  ): SingleProcessor =
    SyncDetector[Option[PondCirculationCommand]](
      config,
      id,
      _.pondCirculation.lastCommandSent,
      reportedCommand,
      _.pondCirculation.lastSyncing,
      (state, lastSyncing: Option[Instant]) =>
        state.modify(_.pondCirculation.lastSyncing).setTo(lastSyncing),
      syncStatusItem,
      eventMatcher
    )
}
