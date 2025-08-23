package calespiga

import calespiga.config.{ApplicationConfig, ConfigLoader}
import calespiga.executor.Executor
import calespiga.model.{Event, State}
import calespiga.mqtt.{
  ActionToMqttProducer,
  Consumer,
  InputTopicsManager,
  MqttToEventInputProcessor,
  Producer
}
import calespiga.openhab.APIClient
import calespiga.persistence.StatePersistence
import calespiga.processor.StateProcessor
import calespiga.userinput.UserInputManager
import cats.effect.{IO, IOApp, ResourceIO}
import fs2.Stream

object Main extends IOApp.Simple {

  private type Resources =
    (
        ApplicationConfig,
        MqttToEventInputProcessor,
        UserInputManager,
        Executor,
        StatePersistence,
        ErrorManager
    )

  private def resources: ResourceIO[Resources] =
    for {
      appConfig <- ConfigLoader.loadResource
      inputTopicsManager = InputTopicsManager.apply
      mqttConsumer <- Consumer(
        appConfig.mqttConfig,
        inputTopicsManager.inputTopics
      )
      mqttInputProcessor = MqttToEventInputProcessor(
        mqttConsumer,
        inputTopicsManager.inputTopicsConversions
      )
      mqttProducer <- Producer(appConfig.mqttConfig)
      mqttActionToProducer = ActionToMqttProducer(mqttProducer)
      openHabApiClient <- APIClient(appConfig.openHabConfig)
      userInputManager = UserInputManager(openHabApiClient)
      executor = Executor(openHabApiClient, mqttActionToProducer)
      errorManager <- ErrorManager()
      statePersistence <- StatePersistence(
        appConfig.statePersistenceConfig,
        errorManager
      )
    } yield (
      appConfig,
      mqttInputProcessor,
      userInputManager,
      executor,
      statePersistence,
      errorManager
    )

  def run: IO[Unit] = {
    resources.use {
      case (
            config,
            mqttInputProcessor,
            userInputManager,
            executor,
            statePersistence,
            errorManager
          ) =>
        val processor = StateProcessor()
        Stream
          .eval(statePersistence.loadState.flatMap {
            case Left(value)  => errorManager.manageError(value).as(State())
            case Right(value) => IO.pure(value)
          })
          .flatMap { initialState =>
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
              .evalMapAccumulate(initialState) { case (current, event) =>
                IO.pure(processor.process(current, event))
              }
              .evalMap { (state, actions) =>
                statePersistence
                  .saveState(state) *> executor.execute(actions).flatMap {
                  errors =>
                    errorManager.manageErrors(errors)
                }
              }
          }
          .compile
          .drain
    }
  }
}
