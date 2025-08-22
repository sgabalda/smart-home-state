package calespiga.processor

import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*
import calespiga.model.RemoteState
import java.time.Instant
import calespiga.processor.RemoteStateProcessor.*
import calespiga.processor.RemoteStateActionProducer.*
import calespiga.model.Switch

trait TemperatureRelatedProcessor {
  def process(
      state: State,
      event: Event.Temperature.TemperatureData,
      timestamp: Instant
  ): (State, Set[Action])
}

object TemperatureRelatedProcessor {

  private final case class Impl(
      batteryFanActionProducer: RemoteSwitchActionProducer,
      electronicsFanActionProducer: RemoteSwitchActionProducer
  ) extends TemperatureRelatedProcessor {

    def process(
        state: State,
        event: Event.Temperature.TemperatureData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      event match {
        case Event.Temperature.BatteryTemperatureMeasured(temperature)
            if temperature != state.temperatures.batteriesTemperature =>
          val newState =
            state.modify(_.temperatures.batteriesTemperature).setTo(temperature)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                "BateriesTemperaturaSHS",
                temperature.toString
              )
            )
          )
        case Event.Temperature.BatteryClosetTemperatureMeasured(temperature)
            if temperature != state.temperatures.batteriesClosetTemperature =>
          val newState =
            state
              .modify(_.temperatures.batteriesClosetTemperature)
              .setTo(temperature)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                "BateriesTemperaturaAdosadaSHS",
                temperature.toString
              )
            )
          )

        case Event.Temperature.ElectronicsTemperatureMeasured(temperature)
            if temperature != state.temperatures.electronicsTemperature =>
          val newState = state
            .modify(_.temperatures.electronicsTemperature)
            .setTo(temperature)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                "ElectronicaTemperaturaSHS",
                temperature.toString
              )
            )
          )

        case Event.Temperature.ExternalTemperatureMeasured(temperature)
            if temperature != state.temperatures.externalTemperature =>
          val newState =
            state.modify(_.temperatures.externalTemperature).setTo(temperature)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                "ExteriorArmarisTemperaturaSHS",
                temperature.toString
              )
            )
          )

        case Event.Temperature.Fans.BatteryFanSwitchReported(stateFan) =>
          val newState = state
            .modify(_.fans.fanBatteries)
            .using(_.process(RemoteState.Event(stateFan), timestamp))
          (
            newState,
            batteryFanActionProducer.produceActionsFor(
              newState.fans.fanBatteries
            )
          )
        case Event.Temperature.Fans.ElectronicsFanSwitchReported(stateFan) =>
          val newState = state
            .modify(_.fans.fanElectronics)
            .using(_.process(RemoteState.Event(stateFan), timestamp))
          (
            newState,
            electronicsFanActionProducer.produceActionsFor(
              newState.fans.fanElectronics
            )
          )
        case Event.Temperature.Fans.BatteryFanSwitchManualChanged(status) =>
          val newState = state
            .modify(_.fans.fanBatteries)
            .using(_.process(RemoteState.Command(status), timestamp))
          (
            newState,
            batteryFanActionProducer.produceActionsFor(
              newState.fans.fanBatteries
            )
          )
        case Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(status) =>
          val newState = state
            .modify(_.fans.fanElectronics)
            .using(_.process(RemoteState.Command(status), timestamp))
          (
            newState,
            electronicsFanActionProducer.produceActionsFor(
              newState.fans.fanElectronics
            )
          )
        case _ =>
          (
            state,
            Set.empty
          ) // needed for the case when temperatures don't change
      }
    }

  }

  def apply(
      batteryFanActionProducer: RemoteSwitchActionProducer =
        RemoteStateActionProducer.forSwitchWithUIItems(
          "VentiladorBateriesStatusSHS",
          "fan/batteries/set"
        ),
      electronicsFanActionProducer: RemoteSwitchActionProducer =
        RemoteStateActionProducer.forSwitchWithUIItems(
          "VentiladorElectronicaStatusSHS",
          "fan/electronics/set"
        )
  ): TemperatureRelatedProcessor = Impl(
    batteryFanActionProducer = batteryFanActionProducer,
    electronicsFanActionProducer = electronicsFanActionProducer
  )
}
