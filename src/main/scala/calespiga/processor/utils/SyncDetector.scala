package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.config.SyncDetectorConfig
import java.time.Instant

object SyncDetector {

  val ID_SUFFIX = "-sync-detector"

  def apply[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String,
      isEventRelevant: Event.EventData => Boolean
  ): SingleProcessor =
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
  ) extends SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = if (isEventRelevant(eventData)) {
      val inSync = field1ToCheck(state) == field2ToCheck(state)

      if (inSync) {
        getLastSyncing(state) match
          case Some(value) =>
            val actions = Set(
              Action.SetUIItemValue(statusItem, config.syncText),
              Action.Cancel(
                id
              )
            )
            (setLastSyncing(state, None), actions)
          case None =>
            // do nothing as the actions were already set
            (state, Set.empty)

      } else {
        getLastSyncing(state) match
          case Some(value) =>
            // do nothing as the actions were already set
            (state, Set.empty)
          case None =>
            val actions = Set(
              Action.SetUIItemValue(statusItem, config.syncingText),
              Action.Delayed(
                id,
                Action.SetUIItemValue(statusItem, config.nonSyncText),
                config.timeoutDuration
              )
            )
            (setLastSyncing(state, Some(timestamp)), actions)
      }

    } else {
      (state, Set.empty)
    }
  }

}
