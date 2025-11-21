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
import calespiga.HealthStatusManager.HealthComponentManager

trait StatePersistence {

  def saveState(state: State): IO[Unit]
  def loadState: IO[Either[StateLoadingError, State]]

}

object StatePersistence {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(
      config: StatePersistenceConfig,
      healthComponentManager: HealthComponentManager,
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
        .productL(healthComponentManager.setHealthy)

    def fileUpdate(errorManager: ErrorManager): IO[Unit] =
      currentState.get.flatMap {
        case Some(state) =>
          saveOutput(config.path, state.asJson.noSpaces)
            .redeemWith(
              e =>
                healthComponentManager.setUnhealthy(e.toString) *>
                  errorManager.manageError(
                    StateFileUpdateError(config.path, e)
                  ),
              _ =>
                healthComponentManager.setHealthy *>
                  logger.debug(s"fileUpdate: state saved to ${config.path}")
            )
        case None =>
          healthComponentManager.setHealthy *> logger.warn(
            "fileUpdate: no state to save"
          )
      }
  }

  def apply(
      statePersistenceConfig: StatePersistenceConfig,
      errorManager: ErrorManager,
      currentStateRef: Ref[IO, Option[State]],
      healthComponentManager: HealthComponentManager,
      readInput: String => IO[String] = s =>
        IO.blocking(Files.readString(Paths.get(s))),
      saveOutput: (String, String) => IO[Unit] = (s, o) =>
        IO.blocking(Files.writeString(Paths.get(s), o)).as(())
  ): ResourceIO[StatePersistence] = {
    val impl =
      Impl(
        statePersistenceConfig,
        healthComponentManager,
        currentStateRef,
        readInput,
        saveOutput
      )
    for {
      _ <- (IO.sleep(statePersistenceConfig.storePeriod) *> impl.fileUpdate(
        errorManager
      )).foreverM.background.onFinalize(
        // Save state when resource is finalized (on application shutdown)
        logger.info("StatePersistence: Saving state on finalization") *>
          impl.fileUpdate(errorManager).handleErrorWith { error =>
            logger.error(error)(
              "StatePersistence: Failed to save state on finalization"
            )
          }
      )
    } yield impl
  }

}
