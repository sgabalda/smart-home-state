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
      statusItem: String
  ): StateProcessor.SingleProcessor =
    Impl(config, id + ID_SUFFIX, field1ToCheck, field2ToCheck, getLastSyncing, setLastSyncing, statusItem)

  private final case class Impl[T](
      config: SyncDetectorConfig,
      id: String,
      field1ToCheck: State => T,
      field2ToCheck: State => T,
      getLastSyncing: State => Option[Instant],
      setLastSyncing: (State, Option[Instant]) => State,
      statusItem: String
  ) extends StateProcessor.SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      val inSync = field1ToCheck(state) == field2ToCheck(state)

      if(inSync) {
        val actions = Set(
          Action.SetOpenHabItemValue(statusItem, config.syncText),
          Action.Cancel(
            id
          )
        )
        (setLastSyncing(state, None), actions)
      } else {
        getLastSyncing(state) match
          case Some(value) => 
            //do nothing as the actions were already set
            (state, Set.empty)
          case None =>
            val actions = Set(
              Action.SetOpenHabItemValue(statusItem, config.syncingText),
              Action.Delayed(
                id,
                Action.SetOpenHabItemValue(statusItem, config.nonSyncText),
                config.timeoutDuration
              )
            )
            (setLastSyncing(state, Some(timestamp)), actions)
      }
      
    }
  }

}
