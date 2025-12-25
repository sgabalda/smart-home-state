package calespiga.processor.heater

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.HeaterSignal.SetAutomatic
import calespiga.model.HeaterSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.config.HeaterConfig
import calespiga.processor.utils.SyncDetector
import java.time.Instant

class HeaterDynamicPowerConsumer(
    config: HeaterConfig,
    heaterSyncDetector: SyncDetector
) extends DynamicPowerConsumer {

  private val actions = Actions(config)

  override def currentlyUsedDynamicPower(state: State, now: Instant): Power =

    if (state.heater.lastCommandReceived.contains(SetAutomatic)) {
      heaterSyncDetector.checkIfInSync(state) match
        case SyncDetector.NotInSync(since)
            if now.isAfter(
              since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
            ) =>
          Power.zero
        case _ =>
          state.heater.status match {
            case Some(HeaterSignal.Power500)  => Power.ofFv(500f)
            case Some(HeaterSignal.Power1000) => Power.ofFv(1000f)
            case Some(HeaterSignal.Power2000) => Power.ofFv(2000f)
            case _                            => Power.zero
          }
    } else {
      Power.zero
    }

  override def usePower(
      state: State,
      powerToUse: Power,
      now: Instant
  ): DynamicPowerResult =
    if (
      state.heater.lastCommandReceived.getOrElse(
        HeaterSignal.TurnOff
      ) != SetAutomatic
    ) {
      // heater is not in automatic mode, do not use dynamic power
      DynamicPowerResult(state, Set.empty, Power.zero)
    } else {
      val desiredPowerLevel = heaterSyncDetector.checkIfInSync(state) match {
        case SyncDetector.NotInSync(since)
            if now.isAfter(
              since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
            ) =>
          HeaterSignal.Off
        case _ =>
          if (powerToUse.fv > 2000f) HeaterSignal.Power2000
          else if (powerToUse.fv > 1000f) HeaterSignal.Power1000
          else if (powerToUse.fv > 500f) HeaterSignal.Power500
          else HeaterSignal.Off
      }

      val newState = state
        .modify(_.heater.lastCommandSent)
        .setTo(Some(desiredPowerLevel))

      val powerUsed =
        desiredPowerLevel match {
          case HeaterSignal.Power2000 => Power.ofFv(2000f)
          case HeaterSignal.Power1000 => Power.ofFv(1000f)
          case HeaterSignal.Power500  => Power.ofFv(500f)
          case _                      => Power.zero
        }

      DynamicPowerResult(
        newState,
        actions.commandActionWithResend(desiredPowerLevel),
        powerUsed
      )
    }

}
