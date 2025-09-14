package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.config.OfflineDetectorConfig
import java.time.Instant

object OfflineDetectorProcessor {

  // Microcontroller status constants
  val ONLINE = "En linia"
  val OFFLINE = "Fora de línia"

  private val TEMPERATURES_TIMEOUT_ID = "temperatures-offline-timeout"

  def apply(config: OfflineDetectorConfig): StateProcessor.SingleProcessor = Impl(config)

  private final case class Impl(config: OfflineDetectorConfig) extends StateProcessor.SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      eventData match {
        // Handle startup event - schedule initial offline timeout
        case Event.System.StartupEvent =>
          val offlineAction = Action.SetOpenHabItemValue(config.temperaturesStatusItem, OFFLINE)
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
          val setOnlineAction = Action.SetOpenHabItemValue(config.temperaturesStatusItem, ONLINE)
          
          // Schedule new offline timeout (automatically cancels previous one with same ID)
          val offlineAction = Action.SetOpenHabItemValue(config.temperaturesStatusItem, OFFLINE)
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
