package calespiga.power

import cats.effect.IO
import fs2.Stream

import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{PowerProductionReported, PowerData}
import calespiga.ErrorManager

trait PowerProductionSource {

  def getEnergyProductionInfo: Stream[IO, Either[ErrorManager.Error, PowerData]]

}

object PowerProductionSource {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  trait PowerProductionOnRequestProvider {
    def getCurrentPowerData: IO[PowerProductionData]
  }

  private final case class Impl(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider
  ) extends PowerProductionSource {
    override def getEnergyProductionInfo
        : Stream[IO, Either[ErrorManager.Error, PowerData]] =
      Stream.awakeDelay(config.pollingInterval).evalMap { _ =>
        (for {
          powerData <- provider.getCurrentPowerData
          _ <- logger.info(s"Retrieved power production data: $powerData")
        } yield Right(
          PowerProductionReported(
            powerAvailable = powerData.powerAvailable,
            powerProduced = powerData.powerProduced,
            powerDiscarded = powerData.powerDiscarded,
            linesPower = powerData.linesPower
          )
        )).handleError(err => Left(ErrorManager.Error.PowerInputError(err)))
      }
  }

  def apply(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider
  ): PowerProductionSource =
    Impl(config, provider)

}
