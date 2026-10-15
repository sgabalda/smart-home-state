package calespiga.power

import calespiga.ErrorManager
import calespiga.model.Event.Power.HomeConsumptionPowerReported
import calespiga.model.Event.Power.PowerProductionReadingError
import calespiga.model.Event.Power.PowerStatusReported
import cats.effect.IO
import munit.CatsEffectSuite

class PowerDataSourceSuite extends CatsEffectSuite {

  case class StubProvider(result: IO[PowerProductionData])
      extends PowerDataSource.PowerProductionOnRequestProvider {
    var calls = 0

    override def getCurrentPowerData: IO[PowerProductionData] =
      IO { calls += 1 } *> result
  }

  test("production data is requested and combined with home consumption") {
    val homeConsumption = HomeConsumptionPowerReported(800f)
    val provider =
      StubProvider(
        IO.pure(PowerProductionData(1200f, 1000f, 200f, List(500f, 500f)))
      )

    for {
      result <- PowerDataSource(provider).getEnergyProductionInfo(
        homeConsumption
      )
      callCount <- IO(provider.calls)
    } yield {
      assertEquals(callCount, 1)
      assertEquals(
        result,
        Right(
          PowerStatusReported(
            production = Some(
              PowerStatusReported.PowerProductionReported(
                1200f,
                1000f,
                200f,
                List(500f, 500f)
              )
            ),
            homeConsumption = 800f,
            powerFromNetwork = -200f
          )
        )
      )
    }
  }

  test("network power is positive when home consumption exceeds production") {
    val homeConsumption = HomeConsumptionPowerReported(1300f)
    val provider =
      StubProvider(IO.pure(PowerProductionData(1200f, 1000f, 200f, List.empty)))

    PowerDataSource(provider)
      .getEnergyProductionInfo(homeConsumption)
      .map { result =>
        assertEquals(
          result.map {
            case status: PowerStatusReported => status.powerFromNetwork
            case unexpected                  =>
              fail(s"Expected a power status event, got $unexpected")
          },
          Right(300f)
        )
      }
  }

  test("network power is negative when production exceeds home consumption") {
    val homeConsumption = HomeConsumptionPowerReported(700f)
    val provider =
      StubProvider(IO.pure(PowerProductionData(1200f, 1000f, 200f, List.empty)))

    PowerDataSource(provider)
      .getEnergyProductionInfo(homeConsumption)
      .map { result =>
        assertEquals(
          result.map {
            case status: PowerStatusReported => status.powerFromNetwork
            case unexpected                  =>
              fail(s"Expected a power status event, got $unexpected")
          },
          Right(-300f)
        )
      }
  }

  test("provider errors are returned with a power reading error event") {
    val error = new RuntimeException("Test error")
    val provider = StubProvider(IO.raiseError(error))

    PowerDataSource(provider)
      .getEnergyProductionInfo(HomeConsumptionPowerReported(800f))
      .map { result =>
        assertEquals(
          result,
          Left(
            ErrorManager.ErrorWithEvent(
              PowerProductionReadingError,
              ErrorManager.Error.PowerInputError(error)
            )
          )
        )
      }
  }
}
