package calespiga.processor

import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*
import calespiga.model.RemoteState
import java.time.Instant
import calespiga.processor.RemoteStateProcessor.*
import calespiga.processor.RemoteStateActionProducer.*
import calespiga.model.Switch

object TemperatureRelatedProcessor {

  private final case class Impl(
      batteryFanActionProducer: RemoteSwitchActionProducer,
      electronicsFanActionProducer: RemoteSwitchActionProducer
  ) extends StateProcessor.SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      eventData match {
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
            batteryFanActionProducer.produceActionsForConfirmed(
              newState.fans.fanBatteries, timestamp
            )
          )
        case Event.Temperature.Fans.ElectronicsFanSwitchReported(stateFan) =>
          val newState = state
            .modify(_.fans.fanElectronics)
            .using(_.process(RemoteState.Event(stateFan), timestamp))
          (
            newState,
            electronicsFanActionProducer.produceActionsForConfirmed(
              newState.fans.fanElectronics, timestamp
            )
          )
        case Event.Temperature.Fans.BatteryFanSwitchManualChanged(status) =>
          val newState = state
            .modify(_.fans.fanBatteries)
            .using(_.process(RemoteState.Command(status), timestamp))
          (
            newState,
            batteryFanActionProducer.produceActionsForCommand(
              newState.fans.fanBatteries, timestamp
            )
          )
        case Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(status) =>
          val newState = state
            .modify(_.fans.fanElectronics)
            .using(_.process(RemoteState.Command(status), timestamp))
          (
            newState,
            electronicsFanActionProducer.produceActionsForCommand(
              newState.fans.fanElectronics, timestamp
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
        RemoteStateActionProducer(
          "VentiladorBateriesStatusSHS",
          "fan/batteries/set",
          "VentiladorsInconsistencySHS",
          "ventilador-bateries"
        ),
      electronicsFanActionProducer: RemoteSwitchActionProducer =
        RemoteStateActionProducer(
          "VentiladorElectronicaStatusSHS",
          "fan/electronics/set",
          "VentiladorsInconsistencySHS",
          "ventilador-electronica"
        )
  ): StateProcessor.SingleProcessor = Impl(
    batteryFanActionProducer = batteryFanActionProducer,
    electronicsFanActionProducer = electronicsFanActionProducer
  )
}
