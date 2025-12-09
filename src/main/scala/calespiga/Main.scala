package calespiga

import calespiga.config.ConfigLoader
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
import calespiga.ui.UserInterfaceManager
import calespiga.http.Endpoints
import cats.effect.{IO, IOApp, ResourceIO}
import fs2.Stream
import cats.effect.Ref
import calespiga.power.sunnyBoy.{SunnyBoyAPIClient, SunnyBoyDecoder}
import calespiga.power.PowerProductionSource
import calespiga.config.PowerProductionConfig
import java.time.ZoneId

object Main extends IOApp.Simple {

  private type Resources =
    (
        ErrorManager,
        StatePersistence,
        Stream[IO, Event],
        StateProcessor,
        Executor
    )

  private def buildInputStream(
      mqttInputProcessor: MqttToEventInputProcessor,
      userInterfaceManager: UserInterfaceManager,
      powerProductionSource: PowerProductionSource,
      errorManager: ErrorManager
  ): Stream[IO, Event] = {
    // Process startup event first, then continue with regular events
    (Stream.emit(Right(Event.System.StartupEvent)) ++
      mqttInputProcessor.inputEventsStream
        .merge(
          userInterfaceManager.userInputEventsStream
        )
        .merge(
          powerProductionSource.getEnergyProductionInfo
        ))
      .evalMapFilter {
        case Left(value) =>
          errorManager.manageError(value).as(None)
        case Right(value) =>
          IO.realTimeInstant.map(instant => Some(Event(instant, value)))
      }
  }
  private def powerDeps(
      config: PowerProductionConfig,
      zoneId: ZoneId
  ): ResourceIO[PowerProductionSource] =
    for {
      sunnyBoy <- SunnyBoyAPIClient(
        config.sunnyBoy,
        SunnyBoyDecoder(config.sunnyBoy)
      )
    } yield PowerProductionSource(
      config.powerProductionSource,
      sunnyBoy,
      zoneId
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
      openHabApiClient <- APIClient(
        appConfig.uiConfig.openHabConfig,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.OpenHabRestClient
        ),
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.OpenHabWebsocketClient
        )
      )
      userInterfaceManager <- UserInterfaceManager(
        openHabApiClient,
        appConfig.uiConfig
      ).toResource
      directExecutor = DirectExecutor(
        userInterfaceManager,
        mqttActionToProducer
      )
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
      zoneId = ZoneId.systemDefault()
      processor = StateProcessor(appConfig.processor, mqttBlacklist, zoneId)
      _ <- Endpoints(stateRef, healthStatusManager, appConfig.httpServerConfig)
      powerSource <- powerDeps(appConfig.powerProduction, zoneId)
      inputStream = buildInputStream(
        mqttInputProcessor,
        userInterfaceManager,
        powerSource,
        errorManager
      )
    } yield (
      errorManager,
      statePersistence,
      inputStream,
      processor,
      executor
    )

  def run: IO[Unit] = {
    resources.use {
      case (
            errorManager,
            statePersistence,
            inputStream,
            processor,
            executor
          ) =>
        Stream
          .eval(statePersistence.loadState.flatMap {
            case Left(value)  => errorManager.manageError(value).as(State())
            case Right(value) => IO.pure(value)
          })
          .flatMap { initialState =>
            inputStream
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
