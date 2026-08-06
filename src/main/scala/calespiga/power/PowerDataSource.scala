package calespiga.power

import cats.effect.IO
import fs2.Stream

import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{PowerStatusReported, PowerData}
import calespiga.ErrorManager
import java.time.ZoneId
import calespiga.model.Event.Power.PowerProductionReadingError

trait PowerDataSource {

  def getEnergyProductionInfo: Stream[IO, Either[ErrorManager.Error, PowerData]]

}

object PowerDataSource {

  trait PowerProductionOnRequestProvider {
    def getCurrentPowerData: IO[PowerProductionData]
  }

  private final case class Impl(
      config: PowerProductionSourceConfig,
      provider: PowerProductionOnRequestProvider,
      zoneId: ZoneId
  ) extends PowerDataSource {

    private def getMaybePowerProductionInfo: IO[Option[PowerProductionData]] =
      IO.realTimeInstant
        .map { now =>
          val hour = now.atZone(zoneId).getHour
          Option.when {
            hour >= config.fvStartingHour && hour <= config.fvEndingHour
          }(())
        }
        .flatMap {
          case Some(_) => provider.getCurrentPowerData.map(Some(_))
          case None    => IO.pure(None)
        }

    private def getPowerGridConsumption: IO[Float] = IO.pure(
      0.0f
    ) // Placeholder for actual grid consumption retrieval logic in the future
    override def getEnergyProductionInfo
        : Stream[IO, Either[ErrorManager.Error, PowerData]] =
      Stream
        .awakeDelay(config.pollingInterval)
        .evalMap { _ =>
          (for {
            maybePowerProduction <- getMaybePowerProductionInfo.map(
              _.map(powerData =>
                PowerStatusReported.PowerProductionReported(
                  powerAvailable = powerData.powerAvailable,
                  powerProduced = powerData.powerProduced,
                  powerDiscarded = powerData.powerDiscarded,
                  linesPower = powerData.linesPower
                )
              )
            )
            gridConsumption <- getPowerGridConsumption.map(
              PowerStatusReported.PowerGridConsumptionReported(_)
            )
          } yield Right(
            PowerStatusReported(
              production = maybePowerProduction,
              gridConsumption = gridConsumption
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
  ): PowerDataSource =
    Impl(config, provider, zoneId)

}
