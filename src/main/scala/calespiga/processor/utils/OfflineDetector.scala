package calespiga.processor.utils

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import calespiga.processor.SingleProcessor
import calespiga.model.Event.System.OfflineDetected
import calespiga.model.OfflineOnlineSignal
import calespiga.model.Action.SendFeedbackEvent

object OfflineDetector {

  val ID_SUFFIX = "-offline-detector"

  def apply(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String,
      offlineStateFieldModifier: (State, OfflineOnlineSignal) => State =
        (state, _) => state
  ): SingleProcessor =
    Impl(
      config,
      id + ID_SUFFIX,
      eventMatcher,
      statusItem,
      offlineStateFieldModifier
    )

  private final case class Impl(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String,
      offlineStateFieldModifier: (State, OfflineOnlineSignal) => State
  ) extends SingleProcessor {

    val offlineAction =
      Action.SetUIItemValue(
        statusItem,
        config.offlineText
      )

    val setOnlineAction =
      Action.SetUIItemValue(
        statusItem,
        config.onlineText
      )

    val delayedOfflineAction = Action.Delayed(
      id,
      SendFeedbackEvent(OfflineDetected(id)),
      config.timeoutDuration
    )

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      eventData match {
        // Handle startup event - schedule initial offline timeout
        case Event.System.StartupEvent =>

          (state, Set(delayedOfflineAction))

        // Handle events matching
        case e if eventMatcher(e) =>

          // Set status to online immediately
          (
            offlineStateFieldModifier(state, OfflineOnlineSignal.Online),
            Set(setOnlineAction, delayedOfflineAction)
          )

        case OfflineDetected(offlineId) if offlineId == id =>
          (
            offlineStateFieldModifier(state, OfflineOnlineSignal.Offline),
            Set(offlineAction)
          )

        // All other events - no action needed
        case _ =>
          (state, Set.empty[Action])
      }
    }
  }

}
