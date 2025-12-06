package calespiga.persistence

import calespiga.ErrorManager.Error.StateFileUpdateError
import calespiga.{ErrorManager, ErrorManagerStub}
import calespiga.config.StatePersistenceConfig
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.softwaremill.quicklens.*

import scala.concurrent.duration.*
import scala.language.postfixOps
import calespiga.model.State
import cats.effect.ResourceIO
import calespiga.HealthStatusManager
import calespiga.HealthComponentManagerStub

class StatePersistenceSuite extends CatsEffectSuite {

  private val config = StatePersistenceConfig(
    path = "test",
    storePeriod = 10 second
  )

  private val someState: State = State(
    temperatures = State.Temperatures(
      externalTemperature = Some(20.0),
      batteriesTemperature = Some(30.0),
      electronicsTemperature = Some(40.0),
      goalTemperature = 20.0
    )
  )
  private val someStateJson = someState.asJson.noSpaces

  private def getSut(
      statePersistenceConfig: StatePersistenceConfig,
      errorManager: ErrorManager = ErrorManagerStub(),
      healthComponentManager: HealthStatusManager.HealthComponentManager =
        HealthComponentManagerStub(),
      currentStateRef: Option[State] = None,
      readInput: String => IO[String] = IO.pure,
      saveOutput: (String, String) => IO[Unit] = (_, _) => IO.unit
  ): ResourceIO[StatePersistence] =
    Ref.of[IO, Option[State]](currentStateRef).toResource.flatMap { ref =>
      StatePersistence(
        statePersistenceConfig,
        errorManager,
        ref,
        healthComponentManager,
        readInput,
        saveOutput
      )
    }

  test("StatePersistence should load state upon request") {
    val sut = getSut(
      config,
      readInput = _ => IO.pure(someStateJson),
      saveOutput = (_, _) =>
        IO.raiseError(new Exception("Not expected call to save input"))
    )

    sut.use { statePersistence =>
      for {
        loadedState <- statePersistence.loadState
        _ = assertEquals(loadedState, Right(someState))
      } yield ()
    }
  }
  test("StatePersistence should return an error if reading file fails") {
    val error = new Exception("File not found")
    val sut = getSut(
      config,
      readInput = _ => IO.raiseError(error),
      saveOutput = (_, _) =>
        IO.raiseError(new Exception("Not expected call to save input"))
    )

    sut.use { statePersistence =>
      for {
        loadedState <- statePersistence.loadState
        _ = assertEquals(
          loadedState,
          Left(ErrorManager.Error.StateReadingFileError(config.path, error))
        )
      } yield ()
    }
  }
  test("StatePersistence should return an error if data read is not valid") {
    val sut = getSut(
      config,
      readInput = _ => IO.pure("blabla"),
      saveOutput = (_, _) =>
        IO.raiseError(new Exception("Not expected call to save input"))
    )
    sut.use { statePersistence =>
      for {
        loadedState <- statePersistence.loadState
        _ = assert(loadedState.isLeft)
        _ = loadedState match {
          case Left(ErrorManager.Error.StateParsingError(path, error)) =>
            assertEquals(path, config.path)
            assert(error.getMessage.contains("blabla"))
          case _ => fail("Expected StateParsingError")
        }
      } yield ()
    }
  }
  test(
    "StatePersistence should store state after storePeriod, but not before"
  ) {
    Ref[IO].of[Option[(String, String)]](None).flatMap { ref =>
      val sut = getSut(
        config,
        readInput =
          _ => IO.raiseError(new Exception("Not expected call to load file")),
        saveOutput = (path, state) => ref.set(Some((path, state))).as(())
      )
      val program = sut.use { statePersistence =>
        for {
          _ <- statePersistence.saveState(someState)
          before <- ref.get
          _ <- IO.sleep(config.storePeriod + (10 seconds))
          after <- ref.get
        } yield {
          assertEquals(before, None)
          after match {
            case Some((path, state)) =>
              assertEquals(path, config.path)
              assertEquals(state, someStateJson)
            case None => fail("Expected state to be saved")
          }
        }
      }
      TestControl.executeEmbed(program)
    }
  }
  test("StatePersistence should store the last state, not the previous one") {
    Ref[IO].of[Option[(String, String)]](None).flatMap { ref =>
      val anotherState = someState
        .modify(_.temperatures.batteriesClosetTemperature)
        .setTo(someState.temperatures.batteriesClosetTemperature.map(_ + 1))
      val sut = getSut(
        config,
        readInput =
          _ => IO.raiseError(new Exception("Not expected call to read input")),
        saveOutput = (path, state) => ref.set(Some((path, state))).as(())
      )
      val program = sut.use { statePersistence =>
        for {
          _ <- statePersistence.saveState(anotherState)
          _ <- IO.sleep(config.storePeriod / 2)
          _ <- statePersistence.saveState(someState)
          before <- ref.get
          _ <- IO.sleep(config.storePeriod + (10 seconds))
          after <- ref.get
        } yield {
          assertEquals(before, None)
          after match {
            case Some((path, state)) =>
              assertEquals(path, config.path)
              assertEquals(state, someStateJson)
            case None => fail("Expected state to be saved")
          }
        }
      }
      TestControl.executeEmbed(program)
    }
  }
  test(
    "StatePersistence should report to ErrorManager any error on saving the state"
  ) {
    val program = Ref[IO].of[Option[ErrorManager.Error]](None).flatMap { ref =>
      val error = new Exception("expected error")
      val sut = getSut(
        config,
        errorManager = ErrorManagerStub(e => ref.set(Some(e))),
        readInput =
          _ => IO.raiseError(new Exception("Not expected call to read input")),
        saveOutput = (_, _) => IO.raiseError(error)
      )
      sut.use { statePersistence =>
        for {
          _ <- statePersistence.saveState(someState)
          before <- ref.get
          _ <- IO.sleep(config.storePeriod + (2 seconds))
          after <- ref.get
        } yield {
          assertEquals(before, None)
          after match {
            case Some(StateFileUpdateError(path, e)) =>
              assertEquals(path, config.path)
              assertEquals(e, error)
            case Some(other) =>
              fail(s"Expected StateFileUpdateError but reported $other")
            case None => fail("Expected error to be reported")
          }
        }
      }
    }
    TestControl.executeEmbed(program)
  }

  test(
    "StatePersistence should set unhealthy if any error on saving the state"
  ) {
    val program = Ref[IO].of[Boolean](false).flatMap { ref =>
      val error = new Exception("expected error")
      val sut = getSut(
        config,
        healthComponentManager =
          HealthComponentManagerStub(onUnhealthy = ref.set(true)),
        saveOutput = (_, _) => IO.raiseError(error)
      )
      sut.use { statePersistence =>
        for {
          _ <- statePersistence.saveState(someState)
          before <- ref.get
          _ <- IO.sleep(config.storePeriod + (2 seconds))
          after <- ref.get
        } yield {
          assertEquals(before, false)
          assertEquals(after, true)
        }
      }
    }
    TestControl.executeEmbed(program)
  }
  test("StatePersistence should set healthy if saving the state succeeds") {
    Ref[IO].of[Boolean](false).flatMap { ref =>
      val anotherState = someState
        .modify(_.temperatures.batteriesClosetTemperature)
        .setTo(someState.temperatures.batteriesClosetTemperature.map(_ + 1))
      val sut = getSut(
        config,
        healthComponentManager =
          HealthComponentManagerStub(onHealthy = ref.set(true)),
        readInput =
          _ => IO.raiseError(new Exception("Not expected call to read input"))
      )
      val program = sut.use { statePersistence =>
        for {
          _ <- statePersistence.saveState(anotherState)
          before <- ref.get
          _ <- IO.sleep(config.storePeriod + (10 seconds))
          after <- ref.get
        } yield {
          assertEquals(before, false)
          assertEquals(after, true)
        }
      }
      TestControl.executeEmbed(program)
    }
  }
}
