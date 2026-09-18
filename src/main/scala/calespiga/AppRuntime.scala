package calespiga

import calespiga.model.State
import cats.effect.IO
import cats.effect.kernel.Outcome
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AppRuntime {
  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def run(resources: AppResources): IO[Unit] = {
    logger.info("Starting application runtime") *>
      Stream
        .eval(resources.statePersistence.loadState.flatMap {
          case Left(value) =>
            resources.errorManager.manageError(value).as(State())
          case Right(value) => IO.pure(value)
        })
        .flatMap { initialState =>
          resources.inputStream
            .evalMapAccumulate(initialState) { case (current, event) =>
              resources.processor.process(current, event)
            }
            .evalMap { (state, actions) =>
              resources.statePersistence
                .saveState(state) *> resources.executor
                .execute(actions)
                .flatMap { errors =>
                  resources.errorManager.manageErrors(errors)
                }
            }
        }
        .compile
        .drain
        .guaranteeCase {
          case Outcome.Succeeded(_) =>
            logger.info("Application runtime shut down cleanly")
          case Outcome.Canceled() =>
            logger.warn("Application runtime was cancelled during shutdown")
          case Outcome.Errored(reason) =>
            logger.error(reason)("Application runtime shut down with an error")
        }
  }
}
