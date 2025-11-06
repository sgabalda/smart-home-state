package calespiga.model

import calespiga.model.State.{Fans, Temperatures, FeatureFlags}

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
      goalTemperature: Double = 20.0
  )
  case class Fans(
      fanBatteriesLatestCommandReceived: FanSignal.UserCommand =
        FanSignal.SetAutomatic,
      fanBatteriesLatestCommandSent: Option[FanSignal.ControllerState] = None,
      fanBatteriesStatus: FanSignal.ControllerState = FanSignal.Off,
      fanBatteriesLastSyncing: Option[java.time.Instant] = None,
      fanElectronicsLatestCommandReceived: FanSignal.UserCommand =
        FanSignal.SetAutomatic,
      fanElectronicsLatestCommandSent: Option[FanSignal.ControllerState] = None,
      fanElectronicsStatus: FanSignal.ControllerState = FanSignal.Off,
      fanElectronicsLastSyncing: Option[java.time.Instant] = None
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
