package calespiga.power

import cats.effect.{IO, ResourceIO, Resource}
import fs2.Stream

import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{
  PowerProductionReported,
  PowerProductionError,
  PowerData
}

trait PowerProductionSource {

  def getEnergyProductionInfo: Stream[IO, PowerData]

}

object PowerProductionSource {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  trait PowerProductionOnRequestProvider {
    def getCurrentPowerData: IO[PowerProductionData]
  }

  private final case class AuthToken(token: String)

  private final case class Impl(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider
  ) extends PowerProductionSource {
    override def getEnergyProductionInfo: Stream[IO, PowerData] =
      Stream.awakeDelay(config.pollingInterval).evalMap { _ =>
        (for {
          powerData <- provider.getCurrentPowerData
          _ <- logger.debug(s"Retrieved power production data: $powerData")
        } yield PowerProductionReported(
          powerAvailable = powerData.powerAvailable,
          powerProduced = powerData.powerProduced,
          powerDiscarded = powerData.powerDiscarded,
          linesPower = powerData.linesPower
        )).handleErrorWith(err =>
          logger
            .error(err)(
              s"Error while retrieving power production data: ${err.getMessage}"
            )
            .as(
              PowerProductionError
            )
        )
      }
  }

  def apply(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider
  ): ResourceIO[PowerProductionSource] =
    Resource.pure(Impl(config, provider))

}
