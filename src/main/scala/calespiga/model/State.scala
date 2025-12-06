package calespiga.model

import calespiga.model.State.{
  Fans,
  Temperatures,
  FeatureFlags,
  Heater,
  PowerProduction
}

case class State(
    featureFlags: FeatureFlags = FeatureFlags(),
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans(),
    heater: Heater = Heater(),
    powerProduction: PowerProduction = PowerProduction()
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

  case class PowerProduction(
      powerAvailable: Option[Float] = None,
      powerProduced: Option[Float] = None,
      powerDiscarded: Option[Float] = None,
      linesPower: List[Float] = List.empty,
      lastUpdate: Option[java.time.Instant] = None,
      lastProducedPower: Option[java.time.Instant] = None
  )

  case class FeatureFlags(
      // to be removed when heater is controlled by SHS
      heaterManagementEnabled: Boolean = false
  )
}
