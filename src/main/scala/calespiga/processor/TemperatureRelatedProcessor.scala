package calespiga.processor

import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*

trait TemperatureRelatedProcessor {
  def process(
      state: State,
      event: Event.Temperature.TemperatureData
  ): (State, Set[Action])
}

object TemperatureRelatedProcessor {

  def apply(): TemperatureRelatedProcessor =
    (state: State, event: Event.Temperature.TemperatureData) => {
      event match {
        case Event.Temperature.BatteryTemperatureMeasured(temperature) =>
          val newState =
            state.modify(_.temperatures.batteriesTemperature).setTo(temperature)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                "BateriesTemperatura",
                temperature.toString
              )
            )
          )

        case Event.Temperature.ElectronicsTemperatureMeasured(temperature) =>
          val newState = state
            .modify(_.temperatures.electronicsTemperature)
            .setTo(temperature)
          (newState, Set.empty)

        case Event.Temperature.ExternalTemperatureMeasured(temperature) =>
          val newState =
            state.modify(_.temperatures.externalTemperature).setTo(temperature)
          (newState, Set.empty)

        case Event.Temperature.Fans.BatteryFanSwitchReported(stateFan) =>
          val newState = state.modify(_.fans.fanBatteries).setTo(stateFan)
          (newState, Set.empty)
        case Event.Temperature.Fans.ElectronicsFanSwitchReported(stateFan) =>
          val newState = state.modify(_.fans.fanElectronics).setTo(stateFan)
          (newState, Set.empty)
        case Event.Temperature.Fans.BatteryFanSwitchManualChanged(status) =>
          println(s"Battery fan switch manual changed: $status")
          (state, Set.empty)
        case Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(status) =>
          println(s"Electronics fan switch manual changed: $status")
          (state, Set.empty)
      }
    }

}
