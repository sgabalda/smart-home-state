package calespiga

import calespiga.model.Action
import cats.effect.{IO, Resource, ResourceIO}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits.toFoldableOps
import calespiga.model.Event

trait ErrorManager {

  def manageError(error: ErrorManager.Error): IO[Unit]

  def manageErrors(errors: List[ErrorManager.Error]): IO[Unit] = {
    errors.traverse_(manageError)
  }
}

object ErrorManager {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  sealed trait Error

  case class ErrorWithEvent(event: Event.EventData, error: Error.SpecificError)
      extends Error

  object Error {
    sealed trait SpecificError extends Error
    case class MqttInputError(throwable: Throwable, topic: String)
        extends SpecificError
    case class OpenHabInputError(throwable: Throwable) extends SpecificError
    case class PowerInputError(throwable: Throwable) extends SpecificError
    case class ExecutionError(throwable: Throwable, action: Action)
        extends SpecificError

    sealed trait StateLoadingError extends SpecificError
    case class StateReadingFileError(path: String, throwable: Throwable)
        extends StateLoadingError
    case class StateParsingError(path: String, error: Throwable)
        extends StateLoadingError
    case class StateFileUpdateError(path: String, error: Throwable)
        extends SpecificError
  }

  private final case class Impl() extends ErrorManager {

    private def manageSpecifiEcError(error: Error.SpecificError): IO[Unit] =
      error match {
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

        case Error.StateReadingFileError(path, throwable) =>
          logger.error(throwable)(s"State file not found: $path")

        case Error.StateParsingError(path, error) =>
          logger.error(error)(
            s"Error parsing state file $path: ${error.getMessage}"
          )
        case Error.StateFileUpdateError(path, error) =>
          logger.error(error)(
            s"Error updating state file $path: ${error.getMessage}"
          )
        case calespiga.ErrorManager.Error.PowerInputError(error) =>
          logger.error(error)(
            s"Error getting power produced data: ${error.getMessage}"
          )
      }

    override def manageError(error: Error): IO[Unit] = error match {
      case calespiga.ErrorManager.ErrorWithEvent(_, specificError) =>
        manageSpecifiEcError(specificError)
      case other: Error.SpecificError =>
        manageSpecifiEcError(other)
    }
  }

  def apply(): ResourceIO[ErrorManager] = Resource.pure(Impl())
}
