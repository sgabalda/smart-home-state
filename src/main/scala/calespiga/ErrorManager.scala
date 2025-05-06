package calespiga

import calespiga.model.Action
import cats.effect.{IO, Resource, ResourceIO}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits.toFoldableOps

trait ErrorManager {

  def manageError(error: ErrorManager.Error): IO[Unit]

  def manageErrors(errors: List[ErrorManager.Error]): IO[Unit] = {
    errors.traverse_(manageError)
  }
}

object ErrorManager {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  sealed trait Error
  object Error {
    case class MqttInputError(throwable: Throwable, topic: String) extends Error
    case class OpenHabInputError(throwable: Throwable) extends Error
    case class ExecutionError(throwable: Throwable, action: Action)
        extends Error
  }

  private final case class Impl() extends ErrorManager {
    override def manageError(error: Error): IO[Unit] = error match {
      case Error.MqttInputError(throwable, topic) =>
        logger.error(throwable)(
          s"Error in topic $topic: ${throwable.getMessage}"
        )

      case Error.ExecutionError(throwable, action) =>
        logger.error(throwable)(
          s"Execution error for action $action: ${throwable.getMessage}"
        )

      case Error.OpenHabInputError(throwable) =>
        logger.error(throwable)(
          s"Error in openHab input: ${throwable.getMessage}"
        )
    }
  }

  def apply(): ResourceIO[ErrorManager] = Resource.pure(Impl())
}
