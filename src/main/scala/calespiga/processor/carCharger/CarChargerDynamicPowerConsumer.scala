package calespiga.processor.carCharger

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.CarChargerSignal.{SetAutomaticFV, SetAutomaticGrid}
import calespiga.model.CarChargerSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.config.CarChargerConfig
import calespiga.processor.utils.SyncDetector
import calespiga.model.CarChargerChargingStatus
import java.time.Instant
import calespiga.model.{BatteryChargeTariff, GridTariff}
import cats.effect.IO

object CarChargerDynamicPowerConsumer {

  /** Determines if the current grid tariff is allowed for charging based on the
    * configured maximum tariff.
    *
    * The logic is:
    *   - If maxGridTariff is None: all tariffs are allowed (default permissive
    *     behavior)
    *   - AllTariffs: always allow
    *   - PlaAndVall: allow only Pla (off-peak) or Vall (valley/night) tariffs
    *   - Vall: allow only Vall (valley/night) tariff
    *   - NoneCharge: never allow (user explicitly blocked grid charging)
    *
    * This ensures the car charger respects user-configured tariff preferences,
    * preventing charging during expensive peak hours.
    */
  private def gridTariffAllowed(state: State): Boolean =
    state.carCharger.maxGridTariff.forall {
      case BatteryChargeTariff.AllTariffs => true
      case BatteryChargeTariff.PlaAndVall =>
        state.grid.currentTariff.exists(t =>
          t == GridTariff.Pla || t == GridTariff.Vall
        )
      case BatteryChargeTariff.Vall =>
        state.grid.currentTariff.contains(GridTariff.Vall)
      case BatteryChargeTariff.NoneCharge => false
    }

  private case class Impl(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ) extends DynamicPowerConsumer {

    private val actions = Actions(config)

    override def uniqueCode: String = config.dynamicConsumerCode

    override def currentlyUsedDynamicPower(
        state: State,
        now: Instant
    ): IO[Power] =
      IO.pure {
        state.carCharger.lastCommandReceived match
          case Some(SetAutomaticFV) =>
            carChargerSyncDetector.checkIfInSync(state) match
              case calespiga.processor.utils.SyncDetector.NotInSync(since)
                  if now.isAfter(
                    since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                  ) =>
                Power.zero
              case _ =>
                state.carCharger.switchStatus match
                  case Some(CarChargerSignal.On) =>
                    state.carCharger.currentPowerWatts
                      .map(p => Power.ofFv(p))
                      .getOrElse(Power.ofFv(config.chargerPowerWatts))
                  case _ => Power.zero
          case Some(SetAutomaticGrid) =>
            carChargerSyncDetector.checkIfInSync(state) match
              case calespiga.processor.utils.SyncDetector.NotInSync(since)
                  if now.isAfter(
                    since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                  ) =>
                Power.zero
              case _ =>
                state.carCharger.switchStatus match
                  case Some(CarChargerSignal.On) =>
                    Power(
                      state.carCharger.currentDynamicFVPower.getOrElse(0f),
                      state.carCharger.currentDynamicGridPower.getOrElse(0f)
                    )
                  case _ => Power.zero
          case _ => Power.zero
      }

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): DynamicPowerResult =
      state.carCharger.lastCommandReceived match
        case Some(SetAutomaticFV) | Some(SetAutomaticGrid) =>
          val isAutomaticFV = state.carCharger.lastCommandReceived.contains(
            SetAutomaticFV
          )

          val desiredControllerState =
            carChargerSyncDetector.checkIfInSync(state) match
              case calespiga.processor.utils.SyncDetector.NotInSync(since)
                  if now.isAfter(
                    since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                  ) =>
                CarChargerSignal.Off
              case _ =>
                val enoughPower =
                  if (isAutomaticFV)
                    powerToUse.fv >= config.chargerPowerWatts
                  else
                    powerToUse.fv +
                      (if gridTariffAllowed(state) then powerToUse.grid
                       else 0f) >= config.chargerPowerWatts
                if (enoughPower) CarChargerSignal.On
                else CarChargerSignal.Off

          val powerUsed =
            if (desiredControllerState == CarChargerSignal.Off) Power.zero
            else if (isAutomaticFV)
              // if the charger reports it's actually charging, prefer the measured current power
              state.carCharger.chargingStatus match
                case Some(CarChargerChargingStatus.Charging) =>
                  Power.ofFv(
                    state.carCharger.currentPowerWatts
                      .getOrElse(config.chargerPowerWatts)
                  )
                case _ => Power.ofFv(config.chargerPowerWatts)
            else
              val fvPower = powerToUse.fv.min(config.chargerPowerWatts)
              val gridPower = config.chargerPowerWatts - fvPower
              Power(
                fvPower,
                if gridTariffAllowed(state) then gridPower else 0f
              )

          val newState = state
            .modify(_.carCharger.lastCommandSent)
            .setTo(Some(desiredControllerState))
            .modify(_.carCharger.currentDynamicFVPower)
            .setTo(Some(powerUsed.fv))
            .modify(_.carCharger.currentDynamicGridPower)
            .setTo(Some(powerUsed.grid))

          DynamicPowerResult(
            newState,
            actions.commandActionWithResend(desiredControllerState),
            powerUsed
          )

        case _ =>
          val newState = state
            .modify(_.carCharger.currentDynamicFVPower)
            .setTo(None)
            .modify(_.carCharger.currentDynamicGridPower)
            .setTo(None)

          // car charger is not in automatic mode, do not use dynamic power
          DynamicPowerResult(newState, Set.empty, Power.zero)

  }

  def apply(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, carChargerSyncDetector)

}
