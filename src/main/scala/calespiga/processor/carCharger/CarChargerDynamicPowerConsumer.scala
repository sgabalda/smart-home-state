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
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CarChargerDynamicPowerConsumer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

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
      carChargerSyncDetector.checkIfInSync(state) match
        case calespiga.processor.utils.SyncDetector.NotInSync(since)
            if now.isAfter(
              since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
            ) =>
          logger
            .warn("Car charger is not in sync, using zero power")
            .as(Power.zero)
        case _ =>
          state.carCharger.lastCommandReceived match
            case Some(SetAutomaticFV) =>
              state.carCharger.switchStatus match
                case Some(CarChargerSignal.On) =>
                  val res = state.carCharger.currentPowerWatts
                    .map(p => Power.ofFv(p))
                    .getOrElse(Power.ofFv(config.chargerPowerWatts))

                  logger
                    .info(
                      s"last command is automatic FV, car charger is on, current power is ${state.carCharger.currentPowerWatts}, so dynamic power used is $res"
                    )
                    .as(res)
                case _ =>
                  logger
                    .info(
                      "last command is automatic FV, but car charger is not on, so dynamic power used is 0"
                    )
                    .as(Power.zero)
            case Some(SetAutomaticGrid) =>
              state.carCharger.switchStatus match
                case Some(CarChargerSignal.On) =>
                  val res = Power(
                    state.carCharger.currentDynamicFVPower.getOrElse(0f),
                    state.carCharger.currentDynamicGridPower.getOrElse(0f)
                  )
                  logger
                    .info(
                      s"last command is automatic Grid, car charger is on, in the state FV: ${state.carCharger.currentDynamicFVPower} grid: ${state.carCharger.currentDynamicGridPower}, so current power is $res"
                    )
                    .as(res)
                case _ =>
                  logger
                    .info(
                      "last command is automatic Grid, but car charger is not on, so dynamic power used is 0"
                    )
                    .as(Power.zero)
            case other =>
              logger
                .info(
                  s"No automatic command received: $other, dynamic power used is 0"
                )
                .as(Power.zero)

    private def applyCommandAndPower(
        powerUsed: Power,
        command: CarChargerSignal.ControllerState,
        state: State
    ): DynamicPowerResult =
      DynamicPowerResult(
        state
          .modify(_.carCharger.lastCommandSent)
          .setTo(Some(command))
          .modify(_.carCharger.currentDynamicFVPower)
          .setTo(Some(powerUsed.fv))
          .modify(_.carCharger.currentDynamicGridPower)
          .setTo(Some(powerUsed.grid)),
        actions.commandActionWithResend(command),
        powerUsed
      )

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      carChargerSyncDetector.checkIfInSync(state) match
        case calespiga.processor.utils.SyncDetector.NotInSync(since)
            if now.isAfter(
              since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
            ) =>
          logger
            .warn(
              s"Car charger not in sync for dynamic power usage since $since, ignoring dynamic power usage"
            )
            .as(DynamicPowerResult(state, Set.empty, Power.zero))
        case _ =>
          state.carCharger.lastCommandReceived match
            case Some(SetAutomaticFV) =>
              val command = if (powerToUse.fv >= config.chargerPowerWatts) then
                CarChargerSignal.On
              else CarChargerSignal.Off
              val powerUsed =
                if (command == CarChargerSignal.Off) Power.zero
                else {
                  // if the charger reports it's actually charging, prefer the measured current power
                  state.carCharger.chargingStatus match
                    case Some(CarChargerChargingStatus.Charging) =>
                      Power.ofFv(
                        state.carCharger.currentPowerWatts
                          .getOrElse(config.chargerPowerWatts)
                      )
                    case _ => Power.ofFv(config.chargerPowerWatts)
                }

              IO.pure(
                applyCommandAndPower(powerUsed, command, state)
              )

            case Some(SetAutomaticGrid) =>
              val tariffAllowed = gridTariffAllowed(state)
              val gridAvailablePower =
                if tariffAllowed then powerToUse.grid else 0f
              val enoughPower =
                powerToUse.fv + gridAvailablePower >= config.chargerPowerWatts
              val command = if (enoughPower) then CarChargerSignal.On
              else CarChargerSignal.Off

              val powerUsed =
                if (command == CarChargerSignal.Off) Power.zero
                else {
                  val fvPower = powerToUse.fv.min(config.chargerPowerWatts)
                  val gridPower = config.chargerPowerWatts - fvPower
                  Power(
                    fvPower,
                    if tariffAllowed then gridPower else 0f
                  )
                }

              IO.pure(
                applyCommandAndPower(powerUsed, command, state)
              )
            case _ =>
              val newState = state
                .modify(_.carCharger.currentDynamicFVPower)
                .setTo(None)
                .modify(_.carCharger.currentDynamicGridPower)
                .setTo(None)

              // car charger is not in automatic mode, do not use dynamic power
              logger
                .info(
                  s"Car charger is not in automatic mode, ignoring dynamic power usage and setting both dynamic FV and Grid power to None in the state"
                )
                .as(DynamicPowerResult(newState, Set.empty, Power.zero))

  }

  def apply(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, carChargerSyncDetector)

}
