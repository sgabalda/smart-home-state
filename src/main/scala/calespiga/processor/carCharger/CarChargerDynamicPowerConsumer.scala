package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.BatteryChargeTariff
import calespiga.model.CarChargerChargingStatus
import calespiga.model.CarChargerSignal
import calespiga.model.CarChargerSignal.SetAutomaticFV
import calespiga.model.CarChargerSignal.SetAutomaticGrid
import calespiga.model.GridTariff
import calespiga.model.State
import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.processor.power.dynamic.Power
import calespiga.processor.utils.SyncDetector
import cats.effect.IO
import com.softwaremill.quicklens.*
import java.time.Instant
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

    /** Reports the dynamic power that is effectively being consumed by the
      * charger when it is in automatic mode and synchronized. If the charger is
      * not actually charging, we report zero consumption to avoid reserving
      * power for a device that is not drawing it.
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
                  val activeDynamicUsage = Power(
                    state.carCharger.plannedDynamicFVPower.getOrElse(0f),
                    state.carCharger.plannedDynamicGridPower.getOrElse(0f)
                  )
                  logger
                    .info(
                      s"Automatic mode active, charger is charging and switched on; dynamic usage is $activeDynamicUsage"
                    )
                    .as(activeDynamicUsage)
                case _ =>
                  logger
                    .info(
                      "Automatic mode active, but the charger is not currently drawing power"
                    )
                    .as(Power.zero)
            case other =>
              logger
                .info(
                  s"No automatic mode command received: $other; dynamic usage is zero"
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
      * {{{
      * if not automatic:
      *
      *     clear planned power
      *     clear automaticOnSince
      *     return zero power, no action
      *
      * if sync timeout exceeded:
      *     turn charger off
      *     clear planned power
      *     clear automaticOnSince
      *     return zero power
      *
      * calculate whether enough FV/grid power is available
      *
      * if insufficient:
      *     turn charger off
      *     clear planned power
      *     clear automaticOnSince
      *     return zero power
      *
      * calculate planned power
      *
      * if charging:
      *     turn charger on
      *     clear automaticOnSince
      *     report planned/measured power
      * else if automaticOnSince is absent:
      *     turn charger on
      *     set automaticOnSince = now
      *     report planned power during the grace period
      * else if now - automaticOnSince < grace timeout:
      *     keep charger on
      *     report planned power
      * else:
      *     keep charger on
      *     report zero power
      *
      * }}}
      */
    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      state.carCharger.lastCommandReceived match
        case Some(mode) if mode == SetAutomaticFV || mode == SetAutomaticGrid =>
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
              // When the car is already charging, the real draw is known; otherwise
              // we assume the configured charger power as a safe estimate.
              val requiredChargingPower =
                if (
                  state.carCharger.chargingStatus.contains(
                    CarChargerChargingStatus.Charging
                  )
                )
                  state.carCharger.currentPowerWatts.getOrElse(
                    config.chargerPowerWatts
                  )
                else config.chargerPowerWatts

              val gridChargingAllowed =
                mode == SetAutomaticGrid && gridTariffAllowed(state)
              val availablePowerForCharging =
                if (gridChargingAllowed) powerToUse else powerToUse.withOnlyFv

              val remainingPowerAfterRequest =
                availablePowerForCharging.consumeFvThenGrid(
                  requiredChargingPower
                )

              logger.info(
                s"Car charger power decision: mode=${mode}, " +
                  s"available=${powerToUse}, " +
                  s"grid tariff allowed=$gridChargingAllowed, " +
                  s"considered=${availablePowerForCharging} W, " +
                  s"required=${requiredChargingPower} W, remaining=$remainingPowerAfterRequest"
              ) *> {
                remainingPowerAfterRequest match
                  case Some(remainingPower) =>
                    val plannedChargingPower =
                      availablePowerForCharging - remainingPower

                    state.carCharger.chargingStatus match
                      case Some(CarChargerChargingStatus.Charging) =>
                        logger
                          .info(
                            s"Car charger is already charging, reserving ${plannedChargingPower} W"
                          )
                          .as(
                            applyCommandAndPower(
                              Some(plannedChargingPower),
                              plannedChargingPower,
                              CarChargerSignal.On,
                              state,
                              None
                            )
                          )
                      case _ =>
                        val automaticOnSince = state.carCharger.automaticOnSince
                        val onSince = automaticOnSince.getOrElse(now)
                        val gracePeriodExpired = !now.isBefore(
                          onSince.plusMillis(
                            config.automaticOnGracePeriod.toMillis
                          )
                        )
                        val reportedDynamicUsage =
                          if (gracePeriodExpired) Power.zero
                          else plannedChargingPower

                        logger
                          .info(
                            s"Car charger is not charging yet; grace expired? $gracePeriodExpired, reporting $reportedDynamicUsage"
                          )
                          .as(
                            applyCommandAndPower(
                              Some(plannedChargingPower),
                              reportedDynamicUsage,
                              CarChargerSignal.On,
                              state,
                              Some(onSince)
                            )
                          )
                  case None =>
                    logger
                      .info(
                        "Not enough available power, turning car charger off"
                      )
                      .as(
                        applyCommandAndPower(
                          None,
                          Power.zero,
                          CarChargerSignal.Off,
                          state,
                          None
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
