package calespiga.model

import calespiga.model.State.CarCharger
import calespiga.model.State.Fans
import calespiga.model.State.FeatureFlags
import calespiga.model.State.GardenWatering
import calespiga.model.State.GreyWater
import calespiga.model.State.Grid
import calespiga.model.State.Heater
import calespiga.model.State.InfraredStove
import calespiga.model.State.PondPump
import calespiga.model.State.PowerManagement
import calespiga.model.State.Temperatures
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.auto._
import sttp.tapir.Schema

case class State(
    featureFlags: FeatureFlags = FeatureFlags(),
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans(),
    heater: Heater = Heater(),
    infraredStove: InfraredStove = InfraredStove(),
    greyWater: GreyWater = GreyWater(),
    gardenWatering: GardenWatering = GardenWatering(),
    pondPump: PondPump = PondPump(),
    powerManagement: PowerManagement = PowerManagement(),
    grid: Grid = Grid(),
    carCharger: CarCharger = CarCharger()
)

object State {

  given Decoder[State] = Decoder.instance { cursor =>
    for {
      featureFlags <- cursor.getOrElse[FeatureFlags]("featureFlags")(
        FeatureFlags()
      )
      temperatures <- cursor.getOrElse[Temperatures]("temperatures")(
        Temperatures()
      )
      fans <- cursor.getOrElse[Fans]("fans")(Fans())
      heater <- cursor.getOrElse[Heater]("heater")(Heater())
      infraredStove <- cursor.getOrElse[InfraredStove]("infraredStove")(
        InfraredStove()
      )
      greyWater <- cursor.getOrElse[GreyWater]("greyWater")(GreyWater())
      gardenWatering <- cursor.getOrElse[GardenWatering]("gardenWatering")(
        GardenWatering()
      )
      pondPump <- cursor.getOrElse[PondPump]("pondPump")(PondPump())
      powerManagement <-
        cursor.getOrElse[PowerManagement]("powerManagement")(
          PowerManagement()
        )
      grid <- cursor.getOrElse[Grid]("grid")(Grid())
      carCharger <- cursor.getOrElse[CarCharger]("carCharger")(CarCharger())
    } yield State(
      featureFlags,
      temperatures,
      fans,
      heater,
      infraredStove,
      greyWater,
      gardenWatering,
      pondPump,
      powerManagement,
      grid,
      carCharger
    )
  }

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
      lastSyncing: Option[java.time.Instant] = None,
      temperature: Option[Double] = None,
      goalTemperature: Double = 40.0
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

  case class GreyWater(
      mode: GreyWaterMode = GreyWaterMode.Off,
      startHour: Int = 16,
      endHour: Int = 18,
      pumpOn: Option[Boolean] = None,
      lastCommandSent: Option[Boolean] = None,
      lastSyncing: Option[java.time.Instant] = None,
      online: Option[OfflineOnlineSignal] = None
  )

  case class GardenWatering(
      manualDurationMinutes: Int = 10,
      scheduledDurationMinutes: Int = 10,
      periodHours: Int = 24,
      startHour: Int = 22,
      activeUntil: Option[java.time.Instant] = None,
      lastWatering: Option[java.time.Instant] = None,
      nextScheduledAt: Option[java.time.Instant] = None
  )

  case class PondPump(
      status: Option[Boolean] = None,
      lastCommandSent: Option[Boolean] = None,
      lastSyncing: Option[java.time.Instant] = None,
      online: Option[OfflineOnlineSignal] = None
  )

  case class PowerManagement(
      production: PowerProduction = PowerProduction(),
      dynamic: DynamicPower = DynamicPower(),
      homeConsumption: Option[Float] = None,
      powerFromNetwork: Option[Float] = None,
      energyToday: PowerEnergyToday = PowerEnergyToday()
  )
  object PowerManagement {
    // Keep this nested state schema derivation bounded as the top-level state grows.
    given Schema[PowerManagement] = Schema.derived
    given Encoder[PowerManagement] = io.circe.generic.semiauto.deriveEncoder

    given Decoder[PowerManagement] = Decoder.instance { cursor =>
      for {
        production <- cursor.getOrElse[PowerProduction]("production")(
          PowerProduction()
        )
        dynamic <- cursor.getOrElse[DynamicPower]("dynamic")(DynamicPower())
        homeConsumption <-
          cursor.getOrElse[Option[Float]]("homeConsumption")(None)
        powerFromNetwork <-
          cursor.getOrElse[Option[Float]]("powerFromNetwork")(None)
        energyToday <-
          cursor.getOrElse[PowerEnergyToday]("energyToday")(
            PowerEnergyToday()
          )
      } yield PowerManagement(
        production,
        dynamic,
        homeConsumption,
        powerFromNetwork,
        energyToday
      )
    }
  }

  case class PowerEnergyToday(
      day: Option[String] = None,
      homeConsumptionWh: Float = 0f,
      gridConsumedWh: Float = 0f,
      gridInjectedWh: Float = 0f,
      gridConsumedVallWh: Float = 0f,
      gridConsumedPlaWh: Float = 0f,
      gridConsumedPicWh: Float = 0f,
      gridInjectedVallWh: Float = 0f,
      gridInjectedPlaWh: Float = 0f,
      gridInjectedPicWh: Float = 0f,
      lastHomeMeterEnergyWh: Option[Float] = None,
      lastHomeMeterUpdate: Option[java.time.Instant] = None,
      lastGridPower: Option[Float] = None,
      lastGridPowerUpdate: Option[java.time.Instant] = None,
      lastGridTariff: Option[GridTariff] = None
  )
  object PowerEnergyToday {
    given Schema[PowerEnergyToday] = Schema.derived
  }

  case class PowerProduction(
      powerAvailable: Option[Float] = None,
      powerProduced: Option[Float] = None,
      powerDiscarded: Option[Float] = None,
      linesPower: List[Float] = List.empty,
      lastUpdate: Option[java.time.Instant] = None,
      lastProducedPower: Option[java.time.Instant] = None,
      lastError: Option[java.time.Instant] = None
  )
  object PowerProduction {
    given Schema[PowerProduction] = Schema.derived
  }

  case class DynamicPower(
      consumersOrder: Seq[String] = Seq.empty,
      lastCycleAt: Option[java.time.Instant] = None
  )
  object DynamicPower {
    given Schema[DynamicPower] = Schema.derived
    given Decoder[DynamicPower] = Decoder.instance { cursor =>
      for {
        consumersOrder <-
          cursor.getOrElse[Seq[String]]("consumersOrder")(Seq.empty)
        lastCycleAt <-
          cursor.getOrElse[Option[java.time.Instant]]("lastCycleAt")(None)
      } yield DynamicPower(consumersOrder, lastCycleAt)
    }
  }

  case class Grid(
      currentTariff: Option[GridTariff] = None,
      availablePower: Option[Float] = None
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
      greyWaterEnabled: Boolean = false
  )
  object FeatureFlags:
    given Decoder[FeatureFlags] = Decoder.instance { cursor =>
      cursor
        .get[Option[Boolean]]("greyWaterEnabled")
        .map(enabled => FeatureFlags(enabled.getOrElse(false)))
    }
}
