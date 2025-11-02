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
  case class Fans(
      fanManagementAutomatic: Switch.Status = Switch.Off,
      fanBatteries: RemoteSwitch = RemoteSwitch(),
      fanElectronics: RemoteSwitch = RemoteSwitch()
  )

  case class Heater(
      status: Option[HeaterSignal.ControllerState] = None,
      lastCommandSent: Option[HeaterSignal.ControllerState] = None,
      lastCommandReceived: Option[HeaterSignal.UserCommand] = None,
      lastChange: Option[java.time.Instant] = None,
      isHot: HeaterSignal.HeaterTermostateState = HeaterSignal.Cold,
      lastTimeHot: Option[java.time.Instant] = None,
      energyToday: Float = 0.0f,
      lastSyncing: Option[java.time.Instant] = None
  )

  case class FeatureFlags(
      fanManagementEnabled: Boolean =
        false, // to be removed when fans are controlled by SHS
      heaterManagementEnabled: Boolean = false
  )
}
