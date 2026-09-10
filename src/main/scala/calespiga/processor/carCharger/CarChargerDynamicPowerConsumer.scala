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
        plannedPower: Option[Power],
        powerUsed: Power,
        command: CarChargerSignal.ControllerState,
        state: State,
        automaticOnSince: Option[Instant]
    ): DynamicPowerResult =
      DynamicPowerResult(
        state
          .modify(_.carCharger.lastCommandSent)
          .setTo(Some(command))
          .modify(_.carCharger.plannedDynamicFVPower)
          .setTo(plannedPower.map(_.fv))
          .modify(_.carCharger.plannedDynamicGridPower)
          .setTo(plannedPower.map(_.grid))
          .modify(_.carCharger.automaticOnSince)
          .setTo(automaticOnSince),
        actions.commandActionWithResend(command),
        powerUsed
      )

    /** As the car charger can not detect if the car is plugged in until it is
      * set to ON and some seconds have passed, the logic implemented starts the
      * charger if there is enough power, but if after some time (a grace
      * timeout) the car is not charging, reports 0 as the used power, so other
      * consumers can use it. If at some point the car is connected and
      * charging, the power used is reported so other donwstream consumers do
      * not use it. The logic is as follows:
      *
      * if not automatic: clear planned power clear automaticOnSince return zero
      * power, no action
      *
      * if sync timeout exceeded: turn charger off clear planned power clear
      * automaticOnSince return zero power
      *
      * calculate whether enough FV/grid power is available if insufficient:
      * turn charger off clear planned power clear automaticOnSince return zero
      * power
      *
      * calculate planned power
      *
      * if charging: turn charger on clear automaticOnSince report
      * planned/measured power else if automaticOnSince is absent: turn charger
      * on set automaticOnSince = now report planned power during the grace
      * period else if now - automaticOnSince <= grace timeout: keep charger on
      * report planned power else: keep charger on report zero power
      */
    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      state.carCharger.lastCommandReceived match
        case Some(SetAutomaticFV) | Some(SetAutomaticGrid) =>
          carChargerSyncDetector.checkIfInSync(state) match
            case calespiga.processor.utils.SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              logger
                .warn(s"Car charger not in sync since $since, turning off")
                .as(
                  applyCommandAndPower(
                    None,
                    Power.zero,
                    CarChargerSignal.Off,
                    state,
                    None
                  )
                )
            case _ =>
              val automaticGrid = state.carCharger.lastCommandReceived.contains(
                SetAutomaticGrid
              )
              val tariffAllowed = !automaticGrid || gridTariffAllowed(state)
              val availableGridPower =
                if tariffAllowed then powerToUse.grid else 0f
              val enoughPower =
                if automaticGrid then
                  powerToUse.fv + availableGridPower >= config.chargerPowerWatts
                else powerToUse.fv >= config.chargerPowerWatts

              logger.info(
                s"Car charger power decision: mode=${
                    if automaticGrid then "automatic grid" else "automatic FV"
                  }, " +
                  s"available FV=${powerToUse.fv} W, available grid=${powerToUse.grid} W, " +
                  s"grid tariff allowed=$tariffAllowed, grid considered=$availableGridPower W, " +
                  s"total considered=${powerToUse.fv + availableGridPower} W, " +
                  s"required=${config.chargerPowerWatts} W, enough power=$enoughPower"
              ) *> {
                if !enoughPower then
                  logger
                    .info("Not enough available power, turning car charger off")
                    .as(
                      applyCommandAndPower(
                        None,
                        Power.zero,
                        CarChargerSignal.Off,
                        state,
                        None
                      )
                    )
                else
                  val plannedPower =
                    if automaticGrid then
                      val fvPower = powerToUse.fv.min(config.chargerPowerWatts)
                      Power(
                        fvPower,
                        if tariffAllowed then config.chargerPowerWatts - fvPower
                        else 0f
                      )
                    else Power.ofFv(config.chargerPowerWatts)

                  state.carCharger.chargingStatus match
                    case Some(CarChargerChargingStatus.Charging) =>
                      val measuredPower =
                        if automaticGrid then
                          Power(
                            state.carCharger.currentPowerWatts
                              .map(_.min(config.chargerPowerWatts))
                              .getOrElse(plannedPower.fv),
                            plannedPower.grid
                          )
                        else
                          Power.ofFv(
                            state.carCharger.currentPowerWatts
                              .getOrElse(config.chargerPowerWatts)
                          )
                      logger
                        .info(s"Car charger is charging, using $measuredPower")
                        .as(
                          applyCommandAndPower(
                            Some(plannedPower),
                            measuredPower,
                            CarChargerSignal.On,
                            state,
                            None
                          )
                        )
                    case _ =>
                      val automaticOnSince = state.carCharger.automaticOnSince
                      val onSince = automaticOnSince.getOrElse(now)
                      val graceExpired = now.isAfter(
                        onSince.plusMillis(
                          config.automaticOnGracePeriod.toMillis
                        )
                      )
                      val powerUsed =
                        if graceExpired then Power.zero else plannedPower
                      logger
                        .info(
                          s"Car charger is not charging; grace period expired? $graceExpired, therefore reporting usage of $powerUsed"
                        )
                        .as(
                          applyCommandAndPower(
                            Some(plannedPower),
                            powerUsed,
                            CarChargerSignal.On,
                            state,
                            Some(onSince)
                          )
                        )
              }
        case _ =>
          val newState = state
            .modify(_.carCharger.plannedDynamicFVPower)
            .setTo(None)
            .modify(_.carCharger.plannedDynamicGridPower)
            .setTo(None)
            .modify(_.carCharger.automaticOnSince)
            .setTo(None)

          logger
            .info("Car charger is not in automatic mode")
            .as(DynamicPowerResult(newState, Set.empty, Power.zero))

  }

  def apply(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, carChargerSyncDetector)

}
