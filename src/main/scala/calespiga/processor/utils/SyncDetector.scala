package calespiga.processor.utils

import calespiga.model.{Action, Event, State}
import calespiga.config.SyncDetectorConfig
import java.time.Instant
import SyncDetector.CheckSyncResult
import calespiga.processor.SingleProcessor

/** Generic synchronization detector processor that can be used for any
  * component by providing two fields to compare for sync status, functions to
  * get and set the last syncing time in the state, and a function to determine
  * if an event is relevant for checking sync. It handles scheduling the timeout
  * for determining non-sync status and updating a UI item with the current sync
  * status.
  *
  * The state does not keep the sync status directly, but instead indirectly via
  * two fileds in the state and the last time they were detected as not in sync.
  * This allows for more flexible logic to determine sync status and to keep
  * track of how long the system has been out of sync.
  *
  * The trait allows to check the sync status on demand via the checkIfInSync
  * method, which can be used by other processors (e.g. to determine if a device
  * can be turned on depending on if the gid is connected or not).
  */

trait SyncDetector extends SingleProcessor {

  /** Checks if the two fields are in sync and returns the sync status,
    * including how long they have been out of sync if applicable. This can be
    * used by other processors to determine if certain actions can be performed
    * depending on the sync status.
    *
    * @param state
    * @return
    */
  def checkIfInSync(state: State): CheckSyncResult

}

object SyncDetector {

  val ID_SUFFIX = "-sync-detector"

  sealed trait CheckSyncResult
  case object InSync extends CheckSyncResult
  case object NotInSyncNow extends CheckSyncResult
  final case class NotInSync(since: Instant) extends CheckSyncResult

  /** Creates a synchronization detector processor.
    *
    * @param config
    *   Configuration for the sync detector, including timeout duration and text
    *   to display for different sync statuses.
    * @param id
    *   Identifier for the component being monitored (used for scheduling and
    *   feedback events).
    * @param field1ToCheck
    *   The first field to compare for determining sync status. This is
    *   typically the last command sent to a device or the desired state.
    * @param field2ToCheck
    *   The second field to compare for determining sync status. This is
    *   typically the last status received from a device or the actual state.
    * @param getLastSyncing
    *   Function to get the last time the system was detected as not in sync
    *   from the state.
    * @param setLastSyncing
    *   Function to set the last time the system was detected as not in sync in
    *   the state.
    * @param statusItem
    *   The UI item to update with the current sync status.
    * @param isEventRelevant
    *   Function to determine if an event is relevant for checking sync status.
    *   Only relevant events will trigger a re-check of the sync status and
    *   potential updates to the UI item.
    * @param messageOffline
    *   Optional message to display when the component goes not in sync. If not
    *   provided, no notification is sent on offline.
    * @return
    */
  def apply[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String,
      isEventRelevant: Event.EventData => Boolean,
      messageOffline: Option[String] = None
  ): SyncDetector =
    Impl(
      config,
      id + ID_SUFFIX,
      field1ToCheck,
      field2ToCheck,
      getLastSyncing,
      setLastSyncing,
      statusItem,
      isEventRelevant,
      messageOffline
    )

  private final case class Impl[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String,
      isEventRelevant: Event.EventData => Boolean,
      messageOffline: Option[String]
  ) extends SyncDetector {

    val idUiUpdate = id
    val idNotification = id + "-notification"

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
                Action.Cancel(idUiUpdate)
              ) ++ (messageOffline match {
                case Some(_) =>
                  Set(Action.Cancel(idNotification))
                case None =>
                  // do nothing as the action was not set
                  Set.empty
              })
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
          ) ++ (messageOffline match {
            case Some(message) =>
              Set(
                Action.Delayed(
                  idNotification,
                  Action.SendNotification(id, message, None),
                  config.timeoutDuration
                )
              )
            case None => Set.empty
          })
          (setLastSyncing(state, Some(timestamp)), actions)

    } else {
      (state, Set.empty)
    }
  }

}
