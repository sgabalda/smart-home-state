package calespiga.processor.utils

import calespiga.model.{Action, Event, State}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import SyncDetector.CheckSyncResult
import calespiga.processor.SingleProcessor

trait SyncDetector extends SingleProcessor {

  def checkIfInSync(state: State): CheckSyncResult

}

object SyncDetector {

  val ID_SUFFIX = "-sync-detector"

  sealed trait CheckSyncResult
  case object InSync extends CheckSyncResult
  case object NotInSyncNow extends CheckSyncResult
  final case class NotInSync(since: Instant) extends CheckSyncResult

  def apply[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String,
      isEventRelevant: Event.EventData => Boolean
  ): SyncDetector =
    Impl(
      config,
      id + ID_SUFFIX,
      field1ToCheck,
      field2ToCheck,
      getLastSyncing,
      setLastSyncing,
      statusItem,
      isEventRelevant
    )

  private final case class Impl[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String,
      isEventRelevant: Event.EventData => Boolean
  ) extends SyncDetector {

    override def checkIfInSync(state: State): CheckSyncResult =
      if (field1ToCheck(state) == field2ToCheck(state))
        InSync
      else
        getLastSyncing(state) match
          case Some(value) =>
            NotInSync(value)
          case None =>
            NotInSyncNow

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = if (isEventRelevant(eventData)) {

      checkIfInSync(state) match
        case InSync =>
          getLastSyncing(state) match
            case Some(value) =>
              val actions = Set(
                Action.SetUIItemValue(statusItem, config.syncText),
                Action.Cancel(id)
              )
              (setLastSyncing(state, None), actions)
            case None =>
              // do nothing as the actions were already set
              (state, Set.empty)

        case NotInSync(since) =>
          (state, Set.empty)

        case NotInSyncNow =>
          val actions = Set(
            Action.SetUIItemValue(statusItem, config.syncingText),
            Action.Delayed(
              id,
              Action.SetUIItemValue(statusItem, config.nonSyncText),
              config.timeoutDuration
            )
          )
          (setLastSyncing(state, Some(timestamp)), actions)

    } else {
      (state, Set.empty)
    }
  }

}
