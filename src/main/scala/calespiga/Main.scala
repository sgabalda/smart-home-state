package calespiga

import calespiga.config.{ApplicationConfig, ConfigLoader}
import calespiga.executor.{DirectExecutor, ScheduledExecutor, Executor}
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
import calespiga.http.Endpoints
import cats.effect.{IO, IOApp, ResourceIO}
import fs2.Stream
import cats.effect.Ref

object Main extends IOApp.Simple {

  private type Resources =
    (
        ApplicationConfig,
        MqttToEventInputProcessor,
        UserInputManager,
        Executor,
        StatePersistence,
        ErrorManager,
        StateProcessor
    )

  private def resources: ResourceIO[Resources] =
    for {
      appConfig <- ConfigLoader.loadResource
      inputTopicsManager = InputTopicsManager.apply
      healthStatusManager <- HealthStatusManager()
      mqttProducer <- Producer(
        appConfig.mqttConfig,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.MqttProducer
        )
      )
      mqttConsumer <- Consumer(
        appConfig.mqttConfig,
        inputTopicsManager.inputTopics,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.MqttConsumer
        )
      )
      mqttInputProcessor = MqttToEventInputProcessor(
        mqttConsumer,
        inputTopicsManager.inputTopicsConversions
      )
      mqttBlacklist <- Ref.of[IO, Set[String]](Set.empty).toResource
      mqttActionToProducer = ActionToMqttProducer(mqttProducer, mqttBlacklist)
      openHabApiClient <- APIClient(appConfig.openHabConfig)
      userInputManager = UserInputManager(openHabApiClient)
      directExecutor = DirectExecutor(openHabApiClient, mqttActionToProducer)
      errorManager <- ErrorManager()
      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager)
      executor = Executor(directExecutor, scheduledExecutor)
      stateRef <- Ref.of[IO, Option[State]](None).toResource
      statePersistence <- StatePersistence(
        appConfig.statePersistenceConfig,
        errorManager,
        stateRef,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.StatePersistence
        )
      )
      processor = StateProcessor(appConfig.processor, mqttBlacklist)
      _ <- Endpoints(stateRef, healthStatusManager, appConfig.httpServerConfig)
    } yield (
      appConfig,
      mqttInputProcessor,
      userInputManager,
      executor,
      statePersistence,
      errorManager,
      processor
    )

  def run: IO[Unit] = {
    resources.use {
      case (
            config,
            mqttInputProcessor,
            userInputManager,
            executor,
            statePersistence,
            errorManager,
            processor
          ) =>
        Stream
          .eval(statePersistence.loadState.flatMap {
            case Left(value)  => errorManager.manageError(value).as(State())
            case Right(value) => IO.pure(value)
          })
          .flatMap { initialState =>
            // Process startup event first, then continue with regular events

            Stream.eval(
              IO.realTimeInstant.map(instant =>
                Event(instant, Event.System.StartupEvent)
              )
            ) ++
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
                  processor.process(current, event)
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
