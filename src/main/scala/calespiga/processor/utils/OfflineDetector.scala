package calespiga.processor.utils

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant
import calespiga.processor.SingleProcessor
import calespiga.model.Event.System.OfflineDetected
import calespiga.model.OfflineOnlineSignal
import calespiga.model.Action.SendFeedbackEvent
import calespiga.model.Action.SendNotification

/** Generic offline detector processor that can be used for any component by
  * providing a matching function for the relevant events and the actions to set
  * online/offline status. It handles scheduling the offline timeout and
  * resetting it on relevant events.
  */
object OfflineDetector {

  val ID_SUFFIX = "-offline-detector"

  /** Creates an offline detector processor.
    *
    * @param config
    *   Configuration for the offline detector, including timeout duration and
    *   online/offline text.
    * @param id
    *   Identifier for the component being monitored (used for scheduling and
    *   feedback events).
    * @param eventMatcher
    *   Function to match relevant events that indicate the component is online.
    * @param statusItem
    *   The UI item to update with online/offline status.
    * @param offlineStateFieldModifier
    *   Function to modify the state when the component is offline. If not
    *   provided, the state is not modified.
    * @param messageOffline
    *   Optional message to display when the component goes offline. If not
    *   provided, no notification is sent on offline.
    * @return
    */
  def apply(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String,
      offlineStateFieldModifier: (State, OfflineOnlineSignal) => State =
        (state, _) => state,
      messageOffline: Option[String] = None
  ): SingleProcessor =
    Impl(
      config,
      id + ID_SUFFIX,
      eventMatcher,
      statusItem,
      offlineStateFieldModifier,
      messageOffline
    )

  private final case class Impl(
      config: OfflineDetectorConfig,
      id: String,
      eventMatcher: Event.EventData => Boolean,
      statusItem: String,
      offlineStateFieldModifier: (State, OfflineOnlineSignal) => State,
      messageOffline: Option[String]
  ) extends SingleProcessor {

    val offlineActions =
      Set(
        Action.SetUIItemValue(statusItem, config.offlineText)
      ) ++ (messageOffline match {
        case Some(message) =>
          Set(SendNotification(id, message, None))
        case None => Set.empty[Action]
      })

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
            offlineActions
          )

        // All other events - no action needed
        case _ =>
          (state, Set.empty[Action])
      }
    }
  }

}
