package calespiga.power

import cats.effect.{IO, Ref}
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import scala.concurrent.duration._
import calespiga.config.PowerProductionSourceConfig
import calespiga.model.Event.Power.{PowerProductionReported}
import calespiga.ErrorManager
import calespiga.model.Event.Power.PowerProductionReadingError

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

  val testConfig = PowerProductionSourceConfig(
    pollingInterval = 15.seconds,
    fvStartingHour = 6,
    fvEndingHour = 21
  )
  val testZoneId = java.time.ZoneId.of("GMT+0")

  test("provider is called every period defined in the config") {
    val program = for {
      callCount <- Ref.of[IO, Int](0)
      data = PowerProductionData(100.0, 50.0, 10.0, List.empty)
      effects = List.fill(3)(callCount.update(_ + 1).as(data))
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](effects)
      stubProvider = StubProvider(effectsQueue)

      count <- PowerProductionSource(
        testConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .take(3)
        .compile
        .drain *> callCount.get

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

      results <- PowerProductionSource(
        testConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .take(3)
        .compile
        .toList

      expected = List(
        Right(PowerProductionReported(100.0, 50.0, 10.0, List.empty)),
        Right(PowerProductionReported(200.0, 75.0, 15.0, List.empty)),
        Right(PowerProductionReported(150.0, 60.0, 12.0, List.empty))
      )
    } yield assertEquals(results, expected)

    TestControl.executeEmbed(program)
  }

  test(
    "if there is an error when calling the provider, the error and event are sent"
  ) {
    val error = new RuntimeException("Test error")

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List(IO.raiseError[PowerProductionData](error))
      )
      stubProvider = StubProvider(effectsQueue)

      result <- PowerProductionSource(
        testConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .take(1)
        .compile
        .lastOrError

    } yield assertEquals(
      result,
      Left(
        ErrorManager.ErrorWithEvent(
          PowerProductionReadingError,
          ErrorManager.Error.PowerInputError(error)
        )
      )
    )

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

      results <- PowerProductionSource(
        testConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .take(3)
        .compile
        .toList

      expected = List(
        Left(
          ErrorManager.ErrorWithEvent(
            PowerProductionReadingError,
            ErrorManager.Error.PowerInputError(error)
          )
        ),
        Right(PowerProductionReported(100.0, 50.0, 10.0, List.empty)),
        Right(PowerProductionReported(100.0, 50.0, 10.0, List.empty))
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

      results <- PowerProductionSource(
        testConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .take(4)
        .compile
        .toList

      expected = List(
        Left(
          ErrorManager.ErrorWithEvent(
            PowerProductionReadingError,
            ErrorManager.Error.PowerInputError(error)
          )
        ),
        Left(
          ErrorManager.ErrorWithEvent(
            PowerProductionReadingError,
            ErrorManager.Error.PowerInputError(error)
          )
        ),
        Right(PowerProductionReported(100.0, 50.0, 10.0, List.empty)),
        Right(PowerProductionReported(100.0, 50.0, 10.0, List.empty))
      )
    } yield assertEquals(results, expected)

    TestControl.executeEmbed(program)
  }

  test(
    "no elements are emitted when current time is outside configured hours"
  ) {
    // Create a config with hours that will never match (future hours)
    val outsideHoursConfig = PowerProductionSourceConfig(
      pollingInterval = 100.millis,
      fvStartingHour = 25,
      fvEndingHour = 25
    )
    val data = PowerProductionData(100.0, 50.0, 10.0, List.empty)

    val program = for {
      effectsQueue <- Ref.of[IO, List[IO[PowerProductionData]]](
        List.fill(5)(IO.pure(data))
      )
      stubProvider = StubProvider(effectsQueue)

      results <- PowerProductionSource(
        outsideHoursConfig,
        stubProvider,
        testZoneId
      ).getEnergyProductionInfo
        .interruptAfter(300.millis)
        .compile
        .toList

    } yield assertEquals(
      results,
      List.empty,
      "No elements should be emitted outside configured hours"
    )

    TestControl.executeEmbed(program)
  }
}
