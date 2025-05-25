package calespiga.persistence

import calespiga.ErrorManager
import calespiga.ErrorManager.Error.*
import calespiga.config.StatePersistenceConfig
import calespiga.model.State
import cats.effect.kernel.Ref
import cats.effect.{IO, ResourceIO}
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.{Files, Paths}

trait StatePersistence {

  def saveState(state: State): IO[Unit]
  def loadState: IO[Either[StateLoadingError, State]]

}

object StatePersistence {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(
      config: StatePersistenceConfig,
      currentState: Ref[IO, Option[State]],
      readInput: String => IO[String],
      saveOutput: (String, String) => IO[Unit]
  ) extends StatePersistence {
    private def readJsonStateFile(path: String): IO[String] =
      readInput(path)

    override def saveState(state: State): IO[Unit] =
      currentState.set(Some(state))

    override def loadState: IO[Either[StateLoadingError, State]] =
      readJsonStateFile(config.path)
        .map(
          decode[State](_).fold(
            error => Left(StateParsingError(config.path, error)),
            state => Right(state)
          )
        )
        .handleError { t =>
          Left(StateReadingFileError(config.path, t))
        }

    def fileUpdate(errorManager: ErrorManager): IO[Unit] =
      currentState.get.flatMap {
        case Some(state) =>
          saveOutput(config.path, state.asJson.noSpaces)
            .redeemWith(
              e =>
                errorManager.manageError(StateFileUpdateError(config.path, e)),
              _ => logger.info(s"fileUpdate: state saved to ${config.path}")
            )
        case None =>
          logger.info("fileUpdate: no state to save")
      }
  }

  def apply(
      statePersistenceConfig: StatePersistenceConfig,
      errorManager: ErrorManager,
      readInput: String => IO[String] = s =>
        IO.blocking(Files.readString(Paths.get(s))),
      saveOutput: (String, String) => IO[Unit] = (s, o) =>
        IO.blocking(Files.writeString(Paths.get(s), o)).as(())
  ): ResourceIO[StatePersistence] = for {
    currentStateRef <- Ref[IO].of[Option[State]](None).toResource
    impl = Impl(statePersistenceConfig, currentStateRef, readInput, saveOutput)
    _ <- (IO.sleep(statePersistenceConfig.storePeriod) *> impl.fileUpdate(
      errorManager
    )).foreverM.background
  } yield impl

}
