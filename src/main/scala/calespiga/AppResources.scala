package calespiga

import java.time.ZoneId

import calespiga.config.{ConfigLoader, PowerProductionConfig}
import calespiga.executor.{DirectExecutor, Executor, ScheduledExecutor}
import calespiga.http.Endpoints
import calespiga.model.Event.FeedbackEventData
import calespiga.model.{Event, State}
import calespiga.mqtt.{
  ActionToMqttProducer,
  InputTopicsManager,
  MqttToEventInputProcessor
}
import calespiga.persistence.StatePersistence
import calespiga.power.PowerDataSource
import calespiga.power.sunnyBoy.{SunnyBoyAPIClient, SunnyBoyDecoder}
import calespiga.processor.StateProcessor
import calespiga.processor.grid.GridTariffSource
import calespiga.ui.UserInterfaceManager
import cats.effect.{IO, Ref, Resource, ResourceIO}
import cats.effect.std.Queue
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class AppResources(
    errorManager: ErrorManager,
    statePersistence: StatePersistence,
    inputStream: Stream[IO, Event],
    processor: StateProcessor,
    executor: Executor
)

object AppResources {
  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def resource(
      externalInterfaces: ExternalInterfaces
  ): ResourceIO[AppResources] =
    for {
      _ <- Resource.eval(
        logger.info("Starting application resource initialization")
      )
      appConfig <- ConfigLoader.loadResource
      inputTopicsManager = InputTopicsManager.apply
      healthStatusManager <- HealthStatusManager()
      mqttProducer <- externalInterfaces.mqttProducer(
        appConfig.mqttConfig,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.MqttProducer
        )
      )
      mqttConsumer <- externalInterfaces.mqttConsumer(
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
      uiBlacklist <- Ref.of[IO, Set[String]](Set.empty).toResource
      mqttActionToProducer = ActionToMqttProducer(mqttProducer, mqttBlacklist)
      openHabApiClient <- externalInterfaces.apiClient(
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
        appConfig.uiConfig,
        uiBlacklist = uiBlacklist
      ).toResource
      feedbackQueue <- Queue.unbounded[IO, FeedbackEventData].toResource
      directExecutor = DirectExecutor(
        userInterfaceManager,
        mqttActionToProducer,
        feedbackQueue
      )
      errorManager <- ErrorManager()
      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager)
      executor = Executor(directExecutor, scheduledExecutor)
      stateRef <- Ref.of[IO, Option[State]](None).toResource
      statePersistence <- externalInterfaces.statePersistence(
        appConfig.statePersistenceConfig,
        errorManager,
        stateRef,
        healthStatusManager.componentHealthManager(
          HealthStatusManager.Component.StatePersistence
        )
      )
      zoneId = ZoneId.of(appConfig.system.timezone)
      processor = StateProcessor(
        appConfig.processor,
        mqttBlacklist,
        uiBlacklist,
        zoneId
      )
      _ <- Endpoints(stateRef, healthStatusManager, appConfig.httpServerConfig)
      powerSource <- powerDeps(appConfig.powerProduction, zoneId)
      tariffSource = GridTariffSource(zoneId)
      inputStream = buildInputStream(
        mqttInputProcessor,
        userInterfaceManager,
        powerSource,
        tariffSource,
        feedbackQueue,
        errorManager
      )
      _ <- Resource.eval(
        logger.info("Application resources initialized successfully")
      )
    } yield AppResources(
      errorManager,
      statePersistence,
      inputStream,
      processor,
      executor
    )

  private def buildInputStream(
      mqttInputProcessor: MqttToEventInputProcessor,
      userInterfaceManager: UserInterfaceManager,
      powerDataSource: PowerDataSource,
      gridTariffSource: GridTariffSource,
      feedbackQueue: Queue[IO, FeedbackEventData],
      errorManager: ErrorManager
  ): Stream[IO, Event] = {
    (Stream.emit(Right(Event.System.StartupEvent)) ++
      mqttInputProcessor.inputEventsStream
        .merge(
          userInterfaceManager.userInputEventsStream
        )
        .merge(
          powerDataSource.getEnergyProductionInfo
        )
        .merge(
          gridTariffSource.events.map(Right(_))
        )
        .merge(
          Stream
            .fromQueueUnterminated(feedbackQueue)
            .map(Right(_))
        ))
      .evalMapFilter {
        case Left(value) =>
          value match
            case ErrorManager.ErrorWithEvent(value, error) =>
              errorManager.manageError(error) *>
                IO.realTimeInstant.map(instant => Some(Event(instant, value)))
            case otherError =>
              errorManager.manageError(otherError).as(None)
        case Right(value) =>
          IO.realTimeInstant.map(instant => Some(Event(instant, value)))
      }
  }

  private def powerDeps(
      config: PowerProductionConfig,
      zoneId: ZoneId
  ): ResourceIO[PowerDataSource] =
    for {
      sunnyBoy <- SunnyBoyAPIClient(
        config.sunnyBoy,
        SunnyBoyDecoder(config.sunnyBoy)
      )
    } yield PowerDataSource(
      config.powerProductionSource,
      sunnyBoy,
      zoneId
    )
}
