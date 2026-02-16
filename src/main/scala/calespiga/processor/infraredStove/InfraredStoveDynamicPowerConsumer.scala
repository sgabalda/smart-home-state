package calespiga.processor.infraredStove

import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import calespiga.model.State
import calespiga.model.InfraredStoveSignal.SetAutomatic
import calespiga.model.InfraredStoveSignal
import calespiga.processor.power.dynamic.Power
import com.softwaremill.quicklens.*
import calespiga.config.InfraredStoveConfig
import calespiga.processor.utils.SyncDetector
import java.time.Instant

private object InfraredStoveDynamicPowerConsumer {

  private case class Impl(
      config: InfraredStoveConfig,
      infraredStoveSyncDetector: SyncDetector
  ) extends DynamicPowerConsumer {

    private val actions = Actions(config)

    override def uniqueCode: String = config.dynamicConsumerCode

    override def currentlyUsedDynamicPower(state: State, now: Instant): Power =

      if (state.infraredStove.lastCommandReceived.contains(SetAutomatic)) {
        infraredStoveSyncDetector.checkIfInSync(state) match
          case SyncDetector.NotInSync(since)
              if now.isAfter(
                since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
              ) =>
            Power.zero
          case _ =>
            state.infraredStove.status match {
              case Some(InfraredStoveSignal.Power600)  => Power.ofFv(600f)
              case Some(InfraredStoveSignal.Power1200) => Power.ofFv(1200f)
              case _                                   => Power.zero
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
        state.infraredStove.lastCommandReceived.getOrElse(
          InfraredStoveSignal.TurnOff
        ) != SetAutomatic
      ) {
        // infrared stove is not in automatic mode, do not use dynamic power
        DynamicPowerResult(state, Set.empty, Power.zero)
      } else {
        val desiredPowerLevel =
          infraredStoveSyncDetector.checkIfInSync(state) match {
            case SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              InfraredStoveSignal.Off
            case _ =>
              if (powerToUse.fv > 1200f) InfraredStoveSignal.Power1200
              else if (powerToUse.fv > 600f) InfraredStoveSignal.Power600
              else InfraredStoveSignal.Off
          }

        val newState = state
          .modify(_.infraredStove.lastCommandSent)
          .setTo(Some(desiredPowerLevel))

        val powerUsed =
          desiredPowerLevel match {
            case InfraredStoveSignal.Power1200 => Power.ofFv(1200f)
            case InfraredStoveSignal.Power600  => Power.ofFv(600f)
            case _                             => Power.zero
          }

        DynamicPowerResult(
          newState,
          actions.commandActionWithResend(desiredPowerLevel),
          powerUsed
        )
      }

  }

  def apply(
      config: InfraredStoveConfig,
      infraredStoveSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, infraredStoveSyncDetector)

}
