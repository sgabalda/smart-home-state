package calespiga

import calespiga.config.{ApplicationConfig, ConfigLoader}
import calespiga.executor.Executor
import calespiga.model.State
import calespiga.mqtt.Consumer
import calespiga.openhab.APIClient
import calespiga.processor.StateProcessor
import cats.effect.{IO, IOApp, ResourceIO}

object Main extends IOApp.Simple {

  private type Resources = (ApplicationConfig, Consumer, Executor, ErrorManager)

  private def resources: ResourceIO[Resources] =
    for {
      appConfig <- ConfigLoader.loadResource
      mqttConsumer <- Consumer(appConfig.mqttSourceConfig)
      openHabApiClient <- APIClient(appConfig.openHabConfig)
      executor <- Executor(openHabApiClient)
      errorManager <- ErrorManager()
    } yield (appConfig, mqttConsumer, executor, errorManager)

  def run: IO[Unit] = {
    resources.use { case (config, consumer, executor, errorManager) =>
      val processor = StateProcessor()
      consumer
        .startConsumer()
        .evalMapFilter {
          case Left(value) =>
            errorManager.manageError(value).as(None)
          case Right(value) =>
            IO.pure(Some(value))
        }
        .evalMapAccumulate(State.empty) { case (current, event) =>
          IO.pure(processor.process(current, event))
        }
        .evalMap { (_, actions) =>
          executor.execute(actions).flatMap { errors =>
            errorManager.manageErrors(errors)
          }
        }
        .compile
        .drain

    }
  }

}
