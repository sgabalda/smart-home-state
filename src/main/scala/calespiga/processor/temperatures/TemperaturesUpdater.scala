package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.model.State
import calespiga.model.Action
import calespiga.model.Event.EventData
import calespiga.model.Event.System.StartupEvent
import java.time.Instant
import calespiga.model.Event.Temperature.*
import com.softwaremill.quicklens.*
import calespiga.config.TemperaturesItemsConfig

private object TemperaturesUpdater {

  private final case class Impl(config: TemperaturesItemsConfig)
      extends SingleProcessor {

    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match
      case StartupEvent =>

        (
          state,
          Set(
            Action.SetUIItemValue(
              config.externalTemperatureItem,
              state.temperatures.goalTemperature.toString
            )
          )
        )
      case BatteryTemperatureMeasured(celsius) =>
        (
          state
            .modify(_.temperatures.batteriesTemperature)
            .setTo(Some(celsius)),
          Set(
            Action.SetUIItemValue(
              config.batteryTemperatureItem,
              celsius.toString
            )
          )
        )
      case BatteryClosetTemperatureMeasured(celsius) =>
        (
          state
            .modify(_.temperatures.batteriesClosetTemperature)
            .setTo(Some(celsius)),
          Set(
            Action.SetUIItemValue(
              config.batteryClosetTemperatureItem,
              celsius.toString
            )
          )
        )
      case ElectronicsTemperatureMeasured(celsius) =>
        (
          state
            .modify(_.temperatures.electronicsTemperature)
            .setTo(Some(celsius)),
          Set(
            Action.SetUIItemValue(
              config.electronicsTemperatureItem,
              celsius.toString
            )
          )
        )
      case ExternalTemperatureMeasured(celsius) =>
        (
          state.modify(_.temperatures.externalTemperature).setTo(Some(celsius)),
          Set(
            Action.SetUIItemValue(
              config.externalTemperatureItem,
              celsius.toString
            )
          )
        )
      case GoalTemperatureChanged(celsius) =>
        (
          state.modify(_.temperatures.goalTemperature).setTo(celsius),
          Set.empty
        )
      case _ =>
        (state, Set.empty)

  }

  def apply(config: TemperaturesItemsConfig): SingleProcessor = Impl(config)
}
