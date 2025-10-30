package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant

object OfflineDetector {

  def apply(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String
  ): SingleProcessor =
    Impl(config, id, eventMatcher, statusItem)

  private final case class Impl(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String
  ) extends SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      eventData match {
        // Handle startup event - schedule initial offline timeout
        case Event.System.StartupEvent =>
          val offlineAction =
            Action.SetOpenHabItemValue(
              statusItem,
              config.offlineText
            )
          val delayedOfflineAction = Action.Delayed(
            id,
            offlineAction,
            config.timeoutDuration
          )
          (state, Set(delayedOfflineAction))

        // Handle events matching
        case e if eventMatcher(e) =>

          // Set status to online immediately
          val setOnlineAction =
            Action.SetOpenHabItemValue(
              statusItem,
              config.onlineText
            )

          // Schedule new offline timeout (automatically cancels previous one with same ID)
          val offlineAction =
            Action.SetOpenHabItemValue(
              statusItem,
              config.offlineText
            )
          val newDelayedOfflineAction = Action.Delayed(
            id,
            offlineAction,
            config.timeoutDuration
          )

          (state, Set(setOnlineAction, newDelayedOfflineAction))

        // All other events - no action needed
        case _ =>
          (state, Set.empty[Action])
      }
    }
  }

}
