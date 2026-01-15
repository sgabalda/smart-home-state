package calespiga.power

import cats.effect.IO
import fs2.Stream

import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{PowerProductionReported, PowerData}
import calespiga.ErrorManager
import java.time.ZoneId
import calespiga.model.Event.Power.PowerProductionReadingError

trait PowerProductionSource {

  def getEnergyProductionInfo: Stream[IO, Either[ErrorManager.Error, PowerData]]

}

object PowerProductionSource {

  trait PowerProductionOnRequestProvider {
    def getCurrentPowerData: IO[PowerProductionData]
  }

  private final case class Impl(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider,
      zoneId: ZoneId
  ) extends PowerProductionSource {
    override def getEnergyProductionInfo
        : Stream[IO, Either[ErrorManager.Error, PowerData]] =
      Stream
        .awakeDelay(config.pollingInterval)
        .evalMapFilter { _ =>
          IO.realTimeInstant.map { now =>
            val hour = now.atZone(zoneId).getHour
            Option.when {
              hour >= config.fvStartingHour && hour <= config.fvEndingHour
            }(())
          }
        }
        .evalMap { _ =>
          (for {
            powerData <- provider.getCurrentPowerData
          } yield Right(
            PowerProductionReported(
              powerAvailable = powerData.powerAvailable,
              powerProduced = powerData.powerProduced,
              powerDiscarded = powerData.powerDiscarded,
              linesPower = powerData.linesPower
            )
          )).handleError(err =>
            Left(
              ErrorManager.ErrorWithEvent(
                PowerProductionReadingError,
                ErrorManager.Error.PowerInputError(err)
              )
            )
          )
        }
  }

  def apply(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider,
      zoneId: ZoneId
  ): PowerProductionSource =
    Impl(config, provider, zoneId)

}
