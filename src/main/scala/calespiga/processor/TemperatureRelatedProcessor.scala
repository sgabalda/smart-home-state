package calespiga.processor

import calespiga.model.event.TemperatureRelated
import calespiga.model.{Action, State}
import com.softwaremill.quicklens.*

trait TemperatureRelatedProcessor {
  def process(
      state: State,
      event: TemperatureRelated
  ): (State, Set[Action])
}

object TemperatureRelatedProcessor {

  def apply(): TemperatureRelatedProcessor =
    (state: State, event: TemperatureRelated) => {
      event match {
        case TemperatureRelated.BatteryTemperatureMeasured(temperature) =>
          val newState =
            state.modify(_.temperatures.batteriesTemperature).setTo(temperature)
          (newState, Set(
            Action.SetOpenHabItemValue(
              "BateriesTemperatura",
              temperature.toString
            )
          ))

        case TemperatureRelated.ElectronicsTemperatureMeasured(temperature) =>
          val newState = state
            .modify(_.temperatures.electronicsTemperature)
            .setTo(temperature)
          (newState, Set.empty)

        case TemperatureRelated.ExternalTemperatureMeasured(temperature) =>
          val newState =
            state.modify(_.temperatures.externalTemperature).setTo(temperature)
          (newState, Set.empty)

        case TemperatureRelated.BatteryFanSwitchReported(stateFan) =>
          val newState = state.modify(_.fans.fanBatteries).setTo(stateFan)
          (newState, Set.empty)
        case TemperatureRelated.ElectronicsFanSwitchReported(stateFan) =>
          val newState = state.modify(_.fans.fanElectronics).setTo(stateFan)
          (newState, Set.empty)
      }
    }

}
