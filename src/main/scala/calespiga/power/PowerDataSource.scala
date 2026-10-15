package calespiga.power

import calespiga.ErrorManager
import calespiga.model.Event.Power.HomeConsumptionPowerReported
import calespiga.model.Event.Power.PowerData
import calespiga.model.Event.Power.PowerProductionReadingError
import calespiga.model.Event.Power.PowerStatusReported
import cats.effect.IO

trait PowerDataSource {

  def getEnergyProductionInfo(
      homeConsumption: HomeConsumptionPowerReported
  ): IO[Either[ErrorManager.Error, PowerData]]

}

object PowerDataSource {

  trait PowerProductionOnRequestProvider {
    def getCurrentPowerData: IO[PowerProductionData]
  }

  private final case class Impl(
      provider: PowerProductionOnRequestProvider
  ) extends PowerDataSource {

    override def getEnergyProductionInfo(
        homeConsumption: HomeConsumptionPowerReported
    ): IO[Either[ErrorManager.Error, PowerData]] =
      provider.getCurrentPowerData
        .map { powerData =>
          Right(
            PowerStatusReported(
              production = Some(
                PowerStatusReported.PowerProductionReported(
                  powerAvailable = powerData.powerAvailable,
                  powerProduced = powerData.powerProduced,
                  powerDiscarded = powerData.powerDiscarded,
                  linesPower = powerData.linesPower
                )
              ),
              homeConsumption = homeConsumption.powerConsumed,
              powerFromNetwork =
                homeConsumption.powerConsumed - powerData.powerProduced
            )
          )
        }
        .handleError(err =>
          Left(
            ErrorManager.ErrorWithEvent(
              PowerProductionReadingError,
              ErrorManager.Error.PowerInputError(err)
            )
          )
        )
  }

  def apply(
      provider: PowerProductionOnRequestProvider
  ): PowerDataSource =
    Impl(provider)

}
