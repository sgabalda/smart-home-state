package calespiga.model

import calespiga.model.State.{Fans, Temperatures, FeatureFlags}
import calespiga.model.RemoteSwitch.*
import calespiga.model.Switch.*

case class State(
    featureFlags: FeatureFlags = FeatureFlags(),
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans(),
    heater: State.Heater = State.Heater()
)

object State {

  case class Temperatures(
      externalTemperature: Option[Double] = None,
      batteriesTemperature: Option[Double] = None,
      batteriesClosetTemperature: Option[Double] = None,
      electronicsTemperature: Option[Double] = None,
      goalTemperature: Option[Double] = None
  )
  case class Heater(
      status: RemoteHeaterPowerState.RemoteHeaterPowerState =
        RemoteHeaterPowerState(),
      lastCommandReceived: Option[
        RemoteHeaterPowerState.RemoteHeaterPowerStatus
      ] = None,
      lastChange: Option[java.time.Instant] = None,
      isHot: Switch.Status = Switch.Off,
      lastTimeHot: Option[java.time.Instant] = None,
      energyToday: Float = 0.0f,
      heaterManagementAutomatic: Switch.Status = Switch.Off
  )
  case class Fans(
      fanManagementAutomatic: Switch.Status = Switch.Off,
      fanBatteries: RemoteSwitch = RemoteSwitch(),
      fanElectronics: RemoteSwitch = RemoteSwitch()
  )

  case class FeatureFlags(
      fanManagementEnabled: Boolean =
        false // to be removed when fans are controlled by SHS
  )
}
