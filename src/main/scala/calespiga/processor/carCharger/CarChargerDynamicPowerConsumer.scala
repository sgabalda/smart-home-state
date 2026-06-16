package calespiga.processor.carCharger

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.CarChargerSignal.SetAutomaticFV
import calespiga.model.CarChargerSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.config.CarChargerConfig
import calespiga.processor.utils.SyncDetector
import calespiga.model.CarChargerChargingStatus
import java.time.Instant

object CarChargerDynamicPowerConsumer {

  private case class Impl(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ) extends DynamicPowerConsumer {

    private val actions = Actions(config)

    override def uniqueCode: String = config.dynamicConsumerCode

    override def currentlyUsedDynamicPower(state: State, now: Instant): Power =
      if (state.carCharger.lastCommandReceived.contains(SetAutomaticFV)) {
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
      } else {
        Power.zero
      }

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): DynamicPowerResult =
      if (!state.carCharger.lastCommandReceived.contains(SetAutomaticFV)) {
        // car charger is not in automatic mode, do not use dynamic power
        DynamicPowerResult(state, Set.empty, Power.zero)
      } else {
        val desiredControllerState =
          carChargerSyncDetector.checkIfInSync(state) match
            case calespiga.processor.utils.SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              CarChargerSignal.Off
            case _ =>
              if (powerToUse.fv >= config.chargerPowerWatts) CarChargerSignal.On
              else CarChargerSignal.Off

        val newState = state
          .modify(_.carCharger.lastCommandSent)
          .setTo(Some(desiredControllerState))

        val powerUsed =
          desiredControllerState match
            case CarChargerSignal.On =>
              // if the charger reports it's actually charging, prefer the measured current power
              state.carCharger.chargingStatus match
                case Some(CarChargerChargingStatus.Charging) =>
                  state.carCharger.currentPowerWatts
                    .map(p => Power.ofFv(p))
                    .getOrElse(Power.ofFv(config.chargerPowerWatts))
                case _ => Power.ofFv(config.chargerPowerWatts)
            case _ => Power.zero

        DynamicPowerResult(
          newState,
          actions.commandActionWithResend(desiredControllerState),
          powerUsed
        )
      }

  }

  def apply(
      config: CarChargerConfig,
      carChargerSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, carChargerSyncDetector)

}
