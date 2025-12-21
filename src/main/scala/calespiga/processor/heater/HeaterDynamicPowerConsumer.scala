package calespiga.processor.heater

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.HeaterSignal.SetAutomatic
import calespiga.model.HeaterSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.config.HeaterConfig

class HeaterDynamicPowerConsumer(config: HeaterConfig)
    extends DynamicPowerConsumer {

  private val actions = Actions(config)

  override def currentlyUsedDynamicPower(state: State): Power =
    if (state.heater.lastCommandReceived.contains(SetAutomatic)) {
      state.heater.status match {
        case Some(HeaterSignal.Power500)  => Power.ofUnusedFV(500f)
        case Some(HeaterSignal.Power1000) => Power.ofUnusedFV(1000f)
        case Some(HeaterSignal.Power2000) => Power.ofUnusedFV(2000f)
        case _                            => Power.zero
      }
    } else {
      Power.zero
    }

  override def usePower(state: State, powerToUse: Power): DynamicPowerResult =
    if (
      state.heater.lastCommandReceived.getOrElse(
        HeaterSignal.TurnOff
      ) != SetAutomatic
    ) {
      // heater is not in automatic mode, do not use dynamic power
      DynamicPowerResult(state, Set.empty, Power.zero)
    } else {
      val desiredPowerLevel =
        if (powerToUse.unusedFV > 2000f) HeaterSignal.Power2000
        else if (powerToUse.unusedFV > 1000f) HeaterSignal.Power1000
        else if (powerToUse.unusedFV > 500f) HeaterSignal.Power500
        else HeaterSignal.Off

      val newState = state
        .modify(_.heater.lastCommandSent)
        .setTo(Some(desiredPowerLevel))

      val powerUsed =
        desiredPowerLevel match {
          case HeaterSignal.Power2000 => Power.ofUnusedFV(2000f)
          case HeaterSignal.Power1000 => Power.ofUnusedFV(1000f)
          case HeaterSignal.Power500  => Power.ofUnusedFV(500f)
          case _                      => Power.zero
        }

      DynamicPowerResult(
        newState,
        actions.commandActionWithResend(desiredPowerLevel),
        powerUsed
      )
    }

}
