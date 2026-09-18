package calespiga.model

import calespiga.model.State.Battery
import calespiga.model.State.CarCharger
import calespiga.model.State.Fans
import calespiga.model.State.FeatureFlags
import calespiga.model.State.Grid
import calespiga.model.State.Heater
import calespiga.model.State.InfraredStove
import calespiga.model.State.PowerManagement
import calespiga.model.State.Temperatures
import sttp.tapir.Schema
import sttp.tapir.generic.auto._

case class State(
    featureFlags: FeatureFlags = FeatureFlags(),
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans(),
    heater: Heater = Heater(),
    infraredStove: InfraredStove = InfraredStove(),
    powerManagement: PowerManagement = PowerManagement(),
    grid: Grid = Grid(),
    battery: Battery = Battery(),
    carCharger: CarCharger = CarCharger()
)

object State {

  /** tapir's built-in schemaForSet returns Schema[collection.Set[T]], not
    * Schema[immutable.Set[T]]. This given bridges the gap for any Set field in
    * the state model.
    */
  given schemaForImmutableSet[T: Schema]: Schema[Set[T]] =
    summon[Schema[T]]
      .asIterable[List]
      .uniqueItems(true)
      .map(list => Some(list.toSet))(_.toList)

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

  case class InfraredStove(
      status: Option[InfraredStoveSignal.ControllerState] = None,
      lastCommandSent: Option[InfraredStoveSignal.ControllerState] = None,
      lastCommandReceived: Option[InfraredStoveSignal.UserCommand] = None,
      lastChange: Option[java.time.Instant] = None,
      manualMaxTimeMinutes: Option[Int] = None,
      lastSetManual: Option[java.time.Instant] = None,
      lastTimeConnected: Option[java.time.Instant] = None,
      energyToday: Float = 0.0f,
      lastOnline: Option[java.time.Instant] = None,
      lastSyncing: Option[java.time.Instant] = None
  )

  case class PowerManagement(
      production: PowerProduction = PowerProduction(),
      dynamic: DynamicPower = DynamicPower()
  )

  case class PowerProduction(
      powerAvailable: Option[Float] = None,
      powerProduced: Option[Float] = None,
      powerDiscarded: Option[Float] = None,
      linesPower: List[Float] = List.empty,
      lastUpdate: Option[java.time.Instant] = None,
      lastProducedPower: Option[java.time.Instant] = None,
      lastError: Option[java.time.Instant] = None
  )

  case class DynamicPower(
      consumersOrder: Seq[String] = Seq.empty
  )

  case class Grid(
      statusConnection: Option[GridSignal.ControllerState] =
        None, // the last status read from the remote grid sensor, used to detect if the grid not connected although the relay may be reporting connected
      lastCommandSent: Option[GridSignal.ControllerState] = None,
      statusRelay: Option[GridSignal.ControllerState] =
        None, // the last status received directly from the relay, used to detect if the relay is not responding
      lastSyncingConnection: Option[java.time.Instant] = None,
      lastSyncingRelay: Option[java.time.Instant] = None,
      devicesRequestedConnection: Set[GridSignal.ActorsConnecting] = Set.empty,
      currentTariff: Option[GridTariff] = None,
      availablePower: Option[Float] = None,
      online: Option[OfflineOnlineSignal] = None
  )
  object Grid:
    given schema: Schema[Grid] = derived[
      Grid
    ] // required for the set to be properly encoded in the OpenAPI docs

  case class Battery(
      status: Option[BatteryStatus] = None,
      lowChargeTariff: Option[BatteryChargeTariff] = None,
      mediumChargeTariff: Option[BatteryChargeTariff] = None,
      online: Option[OfflineOnlineSignal] = None
  )

  case class CarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      lastCommandSent: Option[CarChargerSignal.ControllerState] = None,
      lastCommandReceived: Option[CarChargerSignal.UserCommand] = None,
      lastChange: Option[java.time.Instant] = None,
      lastSyncing: Option[java.time.Instant] = None,
      automaticOnSince: Option[java.time.Instant] = None,
      currentPowerWatts: Option[Float] = None,
      plannedDynamicFVPower: Option[Float] = None,
      plannedDynamicGridPower: Option[Float] = None,
      maxGridTariff: Option[BatteryChargeTariff] = None,
      lastEnergyUpdate: Option[java.time.Instant] = None,
      lastAccumulatedEnergyWh: Option[Float] = None,
      accumulatedAtDayStartWh: Option[Float] = None,
      online: Option[OfflineOnlineSignal] = None,
      chargingStatus: Option[CarChargerChargingStatus] = None
  )

  case class FeatureFlags(
      // to be removed when heater is controlled by SHS
      heaterManagementEnabled: Boolean = false,
      infraredStoveEnabled: Boolean = false,
      gridConnectionEnabled: Boolean = false,
      carChargerManagementEnabled: Boolean = false
  )
}
