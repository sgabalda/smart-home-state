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
    *
    * On the other hand, as the charger can not currently detect whether there
    * is a car connected, the only way to activate it dynamically is:
    *   - if the car is charging and on dynamic mode, then the charging power is
    *     the real one set in the state. If it is still available, keep it ON.
    *     if it is not, turn it OFF
    *   - if the car is not charging, then the dynamic power used is 0 (or the
    *     measured), even if the charger is ON. But on the usePower, check if
    *     there is enough available power to turn it on or not.
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

    /** If it is in automatic mode and in sync, and the status is charging, it
      * means the planned power is really applied. If it is not in automatic
      * mode, or the status is not charging, then the dynamic power used is 0
      */
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
          // if it is in sync, if it is ON and charging, in auto mode, then use the planned power ONLY if charging, and 0 otherwise
          state.carCharger.lastCommandReceived match
            case Some(SetAutomaticFV) | Some(SetAutomaticGrid) =>
              (
                state.carCharger.chargingStatus,
                state.carCharger.switchStatus
              ) match
                case (
                      Some(CarChargerChargingStatus.Charging),
                      Some(CarChargerSignal.On)
                    ) =>
                  val res = Power(
                    state.carCharger.plannedDynamicFVPower.getOrElse(0f),
                    state.carCharger.plannedDynamicGridPower.getOrElse(0f)
                  )
                  logger
                    .info(
                      s"last command is automatic, car charger is charging and swith is ON, so dynamic power used is $res"
                    )
                    .as(res)
                case _ =>
                  logger
                    .info(
                      "last command is automatic FV, but car charger is not on, so dynamic power used is 0"
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
          .modify(_.carCharger.plannedDynamicFVPower)
          .setTo(Some(powerUsed.fv))
          .modify(_.carCharger.plannedDynamicGridPower)
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
          state.carCharger.lastCommandReceived match
            case Some(SetAutomaticFV) | Some(SetAutomaticGrid) =>
              logger
                .warn(
                  s"Car charger not in sync $since and automatic, turning off"
                )
                .as(
                  applyCommandAndPower(Power.zero, CarChargerSignal.Off, state)
                )
            case _ =>
              logger
                .warn(
                  s"Car charger not in sync $since but not automatic, ignoring dynamic power usage"
                )
                .as(DynamicPowerResult(state, Set.empty, Power.zero))
        case _ =>
          state.carCharger.lastCommandReceived match
            case Some(SetAutomaticFV) =>
              val command = if (powerToUse.fv >= config.chargerPowerWatts) then
                CarChargerSignal.On
              else CarChargerSignal.Off
              for {
                _ <- logger.info(
                  s"Automatic FV: as ${powerToUse.fv} >= ${config.chargerPowerWatts} ? then $command"
                )
                powerUsed <-
                  if (command == CarChargerSignal.Off)
                    logger
                      .info("As command is off, power to use is 0")
                      .as(Power.zero)
                  else {
                    // if the charger reports it's actually charging, prefer the measured current power
                    state.carCharger.chargingStatus match
                      case Some(CarChargerChargingStatus.Charging) =>
                        val res = Power.ofFv(
                          state.carCharger.currentPowerWatts
                            .getOrElse(config.chargerPowerWatts)
                        )
                        logger
                          .info(
                            s"Automatic FV: Car charger status is Charging, so power used is $res"
                          )
                          .as(res)

                      case other =>
                        logger
                          .info(
                            s"Automatic FV: Car charger status is not Charging but $other, so using configured power"
                          )
                          .as(
                            Power.ofFv(config.chargerPowerWatts)
                          ) // TODO set it to 0, as it means the car may not be connected now
                  }
              } yield (
                applyCommandAndPower(powerUsed, command, state)
              )

            case Some(SetAutomaticGrid) =>
              val tariffAllowed = gridTariffAllowed(state)
              val gridAvailablePower =
                if tariffAllowed then powerToUse.grid else 0f
              for {
                _ <- logger.info(
                  s"Automatic Grid: tariff ${state.grid.currentTariff}, " +
                    s"and allowed tariff ${state.carCharger.maxGridTariff}, " +
                    s"grid to be used: $tariffAllowed, so grid power: $gridAvailablePower"
                )
                enoughPower =
                  powerToUse.fv + gridAvailablePower >= config.chargerPowerWatts // TODO check if there is a reported charging power, and use that
                command =
                  if (enoughPower) then CarChargerSignal.On
                  else CarChargerSignal.Off
                _ <- logger.info(
                  s"FV(${powerToUse.fv} + Grid($gridAvailablePower) >= ${config.chargerPowerWatts}? => $command"
                )
                powerUsed <- // TODO set it to 0 if the status is not charging, as it means the car may not be connected now
                  if (command == CarChargerSignal.Off)
                    logger
                      .info("Command is off, so using 0 power")
                      .as(Power.zero)
                  else {
                    val fvPower = powerToUse.fv.min(
                      config.chargerPowerWatts
                    ) // TODO check if there is a reported charging power, and use that
                    val gridPower = if tariffAllowed then
                      config.chargerPowerWatts - fvPower
                    else 0f
                    logger
                      .info(s"Power to use: FV($fvPower) + Grid($gridPower)")
                      .as(
                        Power(
                          fvPower,
                          gridPower
                        )
                      )
                  }
              } yield (applyCommandAndPower(powerUsed, command, state))

            case _ =>
              val newState = state
                .modify(_.carCharger.plannedDynamicFVPower)
                .setTo(None)
                .modify(_.carCharger.plannedDynamicGridPower)
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
