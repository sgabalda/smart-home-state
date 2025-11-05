package calespiga.persistence

import calespiga.ErrorManager.Error.StateFileUpdateError
import calespiga.{ErrorManager, ErrorManagerStub}
import calespiga.config.StatePersistenceConfig
import calespiga.model.Fixture
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.softwaremill.quicklens.*

import scala.concurrent.duration.*
import scala.language.postfixOps

class StatePersistenceSuite extends CatsEffectSuite {

  private val config = StatePersistenceConfig(
    path = "test",
    storePeriod = 10 second
  )

  private val someState = Fixture.state
  private val someStateJson = someState.asJson.noSpaces

  test("StatePersistence should load state upon request") {
    val sut = StatePersistence(
      config,
      errorManager = ErrorManagerStub(),
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
    val sut = StatePersistence(
      config,
      errorManager = ErrorManagerStub(),
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
    val sut = StatePersistence(
      config,
      errorManager = ErrorManagerStub(),
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
      val sut = StatePersistence(
        config,
        errorManager = ErrorManagerStub(),
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
      val sut = StatePersistence(
        config,
        errorManager = ErrorManagerStub(),
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
      val sut = StatePersistence(
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
}
