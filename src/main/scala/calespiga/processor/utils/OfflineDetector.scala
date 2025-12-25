package calespiga.processor.utils

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import calespiga.processor.SingleProcessor

object OfflineDetector {

  val ID_SUFFIX = "-offline-detector"

  def apply(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String
  ): SingleProcessor =
    Impl(config, id + ID_SUFFIX, eventMatcher, statusItem)

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
            Action.SetUIItemValue(
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
            Action.SetUIItemValue(
              statusItem,
              config.onlineText
            )

          // Schedule new offline timeout (automatically cancels previous one with same ID)
          val offlineAction =
            Action.SetUIItemValue(
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
