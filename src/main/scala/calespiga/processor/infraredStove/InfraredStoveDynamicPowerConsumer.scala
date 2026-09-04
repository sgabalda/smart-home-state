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
import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

private object InfraredStoveDynamicPowerConsumer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private case class Impl(
      config: InfraredStoveConfig,
      infraredStoveSyncDetector: SyncDetector
  ) extends DynamicPowerConsumer {

    private val actions = Actions(config)

    override def uniqueCode: String = config.dynamicConsumerCode

    override def currentlyUsedDynamicPower(
        state: State,
        now: Instant
    ): IO[Power] =
        if (state.infraredStove.lastCommandReceived.contains(SetAutomatic)) {
          infraredStoveSyncDetector.checkIfInSync(state) match
            case SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              logger.warn(
                s"Infrared stove is not in sync for dynamic power usage since $since, ignoring dynamic power usage"
              ).as(Power.zero)
            case _ =>
              val result = state.infraredStove.status match {
                case Some(InfraredStoveSignal.Power600)  => Power.ofFv(600f)
                case Some(InfraredStoveSignal.Power1200) => Power.ofFv(1200f)
                case _                                   => Power.zero
              }
              logger.info(s"As status is ${state.infraredStove.status}, dynamic power usage for infrared stove is $result").as(result)
        } else {
          logger.info(s"Infrared stove is not in automatic mode, ignoring dynamic power usage").as(Power.zero)
        }
      

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      if (
        state.infraredStove.lastCommandReceived.getOrElse(
          InfraredStoveSignal.TurnOff
        ) != SetAutomatic
      ) {
        // infrared stove is not in automatic mode, do not use dynamic power
        logger.info(s"Infrared stove is not in automatic mode, ignoring dynamic power usage").as(DynamicPowerResult(state, Set.empty, Power.zero))
      } else {
        for{
          desiredPowerLevel <-
            infraredStoveSyncDetector.checkIfInSync(state) match {
              case SyncDetector.NotInSync(since)
                  if now.isAfter(
                    since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                  ) =>
                logger.info(s"Infrared stove is not in sync for dynamic power usage since $since, setting power to Off").as(InfraredStoveSignal.Off)
              case _ =>
                val res = if (powerToUse.fv > 1200f) InfraredStoveSignal.Power1200
                else if (powerToUse.fv > 600f) InfraredStoveSignal.Power600
                else InfraredStoveSignal.Off
                logger.info(s"Desired power level for infrared stove based on available power $powerToUse is $res").as(res)
            }
          
          newState = state
            .modify(_.infraredStove.lastCommandSent)
            .setTo(Some(desiredPowerLevel))


          powerUsed =
            desiredPowerLevel match {
              case InfraredStoveSignal.Power1200 => Power.ofFv(1200f)
              case InfraredStoveSignal.Power600  => Power.ofFv(600f)
              case _                             => Power.zero
            }

        }yield(
          DynamicPowerResult(
            newState,
            actions.commandActionWithResend(desiredPowerLevel),
            powerUsed
          )
        )
      }

  }

  def apply(
      config: InfraredStoveConfig,
      infraredStoveSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, infraredStoveSyncDetector)

}
