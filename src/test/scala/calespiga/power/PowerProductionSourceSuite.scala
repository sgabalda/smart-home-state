package calespiga.power

import cats.effect.{IO, Ref}
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import scala.concurrent.duration._
import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{
  PowerProductionReported,
  PowerProductionError
}

class PowerProductionSourceSuite extends CatsEffectSuite {

  case class StubProvider(
      effectsQueue: Ref[IO, List[IO[PowerProductionData]]]
  ) extends PowerProductionSource.PowerProductionOnRequestProvider {
    override def getCurrentPowerData: IO[PowerProductionData] =
      effectsQueue.modify {
        case head :: tail => (tail, head)
        case Nil          =>
          (Nil, IO.raiseError(new RuntimeException("No more effects in queue")))
      }.flatten
  }

  val testConfig = PowerProductionSourceConfig(pollingInterval = 100.millis)

  test("provider is called every period defined in the config") {
    val program = for {
      callCount <- Ref.of[IO, Int](0)
      data = PowerProductionData(100.0, 50.0, 10.0, List.empty)
      effects = List.fill(3)(callCount.update(_ + 1).as(data))
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](effects)
      stubProvider = StubProvider(effectsQueue)

      count <- PowerProductionSource(testConfig, stubProvider).use { source =>
        source.getEnergyProductionInfo
          .take(3)
          .compile
          .drain *> callCount.get
      }
    } yield assertEquals(count, 3)

    TestControl.executeEmbed(program)
  }

  test("data returned by the provider is sent via the stream") {
    val data1 = PowerProductionData(100.0, 50.0, 10.0, List.empty)
    val data2 = PowerProductionData(200.0, 75.0, 15.0, List.empty)
    val data3 = PowerProductionData(150.0, 60.0, 12.0, List.empty)

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List(IO.pure(data1), IO.pure(data2), IO.pure(data3))
      )
      stubProvider = StubProvider(effectsQueue)

      results <- PowerProductionSource(testConfig, stubProvider).use { source =>
        source.getEnergyProductionInfo
          .take(3)
          .compile
          .toList
      }

      expected = List(
        PowerProductionReported(100.0, 50.0, 10.0, List.empty),
        PowerProductionReported(200.0, 75.0, 15.0, List.empty),
        PowerProductionReported(150.0, 60.0, 12.0, List.empty)
      )
    } yield assertEquals(results, expected)

    TestControl.executeEmbed(program)
  }

  test(
    "if there is an error when calling the provider, the error event is sent"
  ) {
    val error = new RuntimeException("Test error")

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List(IO.raiseError[PowerProductionData](error))
      )
      stubProvider = StubProvider(effectsQueue)

      result <- PowerProductionSource(testConfig, stubProvider).use { source =>
        source.getEnergyProductionInfo
          .take(1)
          .compile
          .lastOrError
      }
    } yield assertEquals(result, PowerProductionError)

    TestControl.executeEmbed(program)
  }

  test("periodic execution continues after an error") {
    val error = new RuntimeException("Test error")
    val data = PowerProductionData(100.0, 50.0, 10.0, List.empty)

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List(
          IO.raiseError[PowerProductionData](error),
          IO.pure(data),
          IO.pure(data)
        )
      )
      stubProvider = StubProvider(effectsQueue)

      results <- PowerProductionSource(testConfig, stubProvider).use { source =>
        source.getEnergyProductionInfo
          .take(3)
          .compile
          .toList
      }

      expected = List(
        PowerProductionError,
        PowerProductionReported(100.0, 50.0, 10.0, List.empty),
        PowerProductionReported(100.0, 50.0, 10.0, List.empty)
      )
    } yield assertEquals(results, expected)

    TestControl.executeEmbed(program)
  }

  test("multiple errors followed by success") {
    val error = new RuntimeException("Test error")
    val data = PowerProductionData(100.0, 50.0, 10.0, List.empty)

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List(
          IO.raiseError[PowerProductionData](error),
          IO.raiseError[PowerProductionData](error),
          IO.pure(data),
          IO.pure(data)
        )
      )
      stubProvider = StubProvider(effectsQueue)

      results <- PowerProductionSource(testConfig, stubProvider).use { source =>
        source.getEnergyProductionInfo
          .take(4)
          .compile
          .toList
      }

      expected = List(
        PowerProductionError,
        PowerProductionError,
        PowerProductionReported(100.0, 50.0, 10.0, List.empty),
        PowerProductionReported(100.0, 50.0, 10.0, List.empty)
      )
    } yield assertEquals(results, expected)

    TestControl.executeEmbed(program)
  }
}
