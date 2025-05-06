package calespiga

import calespiga.config.{ApplicationConfig, ConfigLoader}
import calespiga.executor.Executor
import calespiga.model.{Event, State}
import calespiga.mqtt.{Consumer, InputTopicsManager, MqttToEventInputProcessor}
import calespiga.openhab.APIClient
import calespiga.processor.StateProcessor
import calespiga.userinput.UserInputManager
import cats.effect.{IO, IOApp, ResourceIO}

object Main extends IOApp.Simple {

  private type Resources =
    (
        ApplicationConfig,
        MqttToEventInputProcessor,
        UserInputManager,
        Executor,
        ErrorManager
    )

  private def resources: ResourceIO[Resources] =
    for {
      appConfig <- ConfigLoader.loadResource
      inputTopicsManager = InputTopicsManager.apply
      mqttConsumer <- Consumer(
        appConfig.mqttSourceConfig,
        inputTopicsManager.inputTopics
      )
      mqttInputProcessor = MqttToEventInputProcessor(
        mqttConsumer,
        inputTopicsManager.inputTopicsConversions
      )
      openHabApiClient <- APIClient(appConfig.openHabConfig)
      userInputManager = UserInputManager(openHabApiClient)
      executor <- Executor(openHabApiClient)
      errorManager <- ErrorManager()
    } yield (
      appConfig,
      mqttInputProcessor,
      userInputManager,
      executor,
      errorManager
    )

  def run: IO[Unit] = {
    resources.use {
      case (
            config,
            mqttInputProcessor,
            userInputManager,
            executor,
            errorManager
          ) =>
        val processor = StateProcessor()
        mqttInputProcessor.inputEventsStream
          .merge(
            userInputManager
              .userInputEventsStream()
          )
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
