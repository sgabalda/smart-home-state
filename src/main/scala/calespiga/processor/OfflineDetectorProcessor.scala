package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant

object OfflineDetectorProcessor {

  private val TEMPERATURES_TIMEOUT_ID = "temperatures-offline-timeout"

  def apply(config: OfflineDetectorConfig): SingleProcessor =
    Impl(config)

  private final case class Impl(config: OfflineDetectorConfig)
      extends SingleProcessor {

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
              config.temperaturesStatusItem,
              config.offlineText
            )
          val delayedOfflineAction = Action.Delayed(
            TEMPERATURES_TIMEOUT_ID,
            offlineAction,
            config.timeoutDuration
          )
          (state, Set(delayedOfflineAction))

        // Handle events from Temperatures microcontroller
        case _: Event.Temperature.BatteryTemperatureMeasured |
            _: Event.Temperature.BatteryClosetTemperatureMeasured |
            _: Event.Temperature.ElectronicsTemperatureMeasured |
            _: Event.Temperature.ExternalTemperatureMeasured |
            _: Event.Temperature.Fans.BatteryFanSwitchReported |
            _: Event.Temperature.Fans.ElectronicsFanSwitchReported =>

          // Set status to online immediately
          val setOnlineAction =
            Action.SetOpenHabItemValue(
              config.temperaturesStatusItem,
              config.onlineText
            )

          // Schedule new offline timeout (automatically cancels previous one with same ID)
          val offlineAction =
            Action.SetOpenHabItemValue(
              config.temperaturesStatusItem,
              config.offlineText
            )
          val newDelayedOfflineAction = Action.Delayed(
            TEMPERATURES_TIMEOUT_ID,
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
