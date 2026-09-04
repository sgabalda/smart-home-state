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
import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object HeaterDynamicPowerConsumer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private case class Impl(
      config: HeaterConfig,
      heaterSyncDetector: SyncDetector
  ) extends DynamicPowerConsumer {

    private val actions = Actions(config)

    override def uniqueCode: String = config.dynamicConsumerCode

    override def currentlyUsedDynamicPower(
        state: State,
        now: Instant
    ): IO[Power] =
        if (state.heater.lastCommandReceived.contains(SetAutomatic)) {
          heaterSyncDetector.checkIfInSync(state) match
            case SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              logger.warn(
                s"Heater is not in sync for dynamic power usage since $since, ignoring dynamic power usage"
              ).as(Power.zero)
            case _ =>
              val result = state.heater.status match {
                case Some(HeaterSignal.Power500)  => Power.ofFv(500f)
                case Some(HeaterSignal.Power1000) => Power.ofFv(1000f)
                case Some(HeaterSignal.Power2000) => Power.ofFv(2000f)
                case _                            => Power.zero
              }
              logger.info(s"As status is ${state.heater.status}, dynamic power usage for heater is $result").as(result)
        } else {
          logger.info(s"Heater is not in automatic mode, ignoring dynamic power usage").as(Power.zero)
        }
    

    override def usePower(
        state: State,
        powerToUse: Power,
        now: Instant
    ): IO[DynamicPowerResult] =
      if (
        state.heater.lastCommandReceived.getOrElse(
          HeaterSignal.TurnOff
        ) != SetAutomatic
      ) {
        // heater is not in automatic mode, do not use dynamic power
        logger.info(s"Heater is not in automatic mode, ignoring dynamic power usage").as(DynamicPowerResult(state, Set.empty, Power.zero))
      } else {
        for{
          desiredPowerLevel <- heaterSyncDetector.checkIfInSync(state) match {
            case SyncDetector.NotInSync(since)
                if now.isAfter(
                  since.plusMillis(config.syncTimeoutForDynamicPower.toMillis)
                ) =>
              logger.info(s"Heater is not in sync for dynamic power usage since $since, setting power to Off").as(HeaterSignal.Off)
            case _ =>
              val result = if (powerToUse.fv > 2000f) HeaterSignal.Power2000
              else if (powerToUse.fv > 1000f) HeaterSignal.Power1000
              else if (powerToUse.fv > 500f) HeaterSignal.Power500
              else HeaterSignal.Off

              logger.info(s"Desired power level for heater based on available power $powerToUse is $result").as(result)
          }
          newState = state          .modify(_.heater.lastCommandSent)          .setTo(Some(desiredPowerLevel))
          powerUsed =
            desiredPowerLevel match {
              case HeaterSignal.Power2000 => Power.ofFv(2000f)
              case HeaterSignal.Power1000 => Power.ofFv(1000f)
              case HeaterSignal.Power500  => Power.ofFv(500f)
              case _                      => Power.zero
            }
          _ <- logger.info(s"Dynamic power used for heater is $powerUsed")
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
      config: HeaterConfig,
      heaterSyncDetector: SyncDetector
  ): DynamicPowerConsumer =
    Impl(config, heaterSyncDetector)

}
