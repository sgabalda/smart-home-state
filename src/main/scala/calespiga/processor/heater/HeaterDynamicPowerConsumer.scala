package calespiga.processor.heater

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.HeaterSignal.SetAutomatic
import calespiga.model.HeaterSignal
import calespiga.processor.power.dynamic.DynamicPowerConsumer.Power
import com.softwaremill.quicklens.*
import calespiga.config.HeaterConfig

class HeaterDynamicPowerConsumer(config: HeaterConfig)
    extends DynamicPowerConsumer {

  private val actions = Actions(config)

  override def currentlyUsedDynamicPower(state: State): Power =
    if (state.heater.lastCommandReceived.contains(SetAutomatic)) {
      state.heater.status match {
        case Some(HeaterSignal.Power500)  => Power(500f)
        case Some(HeaterSignal.Power1000) => Power(1000f)
        case Some(HeaterSignal.Power2000) => Power(2000f)
        case _                            => DynamicPowerConsumer.zeroPower
      }
    } else {
      DynamicPowerConsumer.zeroPower
    }

  override def usePower(state: State, powerToUse: Power): DynamicPowerResult =
    if (
      state.heater.lastCommandReceived.getOrElse(
        HeaterSignal.TurnOff
      ) != SetAutomatic
    ) {
      // heater is not in automatic mode, do not use dynamic power
      DynamicPowerResult(state, Set.empty, DynamicPowerConsumer.zeroPower)
    } else {
      val desiredPowerLevel =
        if (powerToUse.unusedFV >= 2000f) HeaterSignal.Power2000
        else if (powerToUse.unusedFV >= 1000f) HeaterSignal.Power1000
        else if (powerToUse.unusedFV >= 500f) HeaterSignal.Power500
        else HeaterSignal.Off

      val newState = state
        .modify(_.heater.lastCommandSent)
        .setTo(Some(desiredPowerLevel))

      val powerUsed =
        desiredPowerLevel match {
          case HeaterSignal.Power2000 => Power(2000f)
          case HeaterSignal.Power1000 => Power(1000f)
          case HeaterSignal.Power500  => Power(500f)
          case _                      => DynamicPowerConsumer.zeroPower
        }

      DynamicPowerResult(
        newState,
        actions.commandActionWithResend(desiredPowerLevel),
        powerUsed
      )
    }

}
