package calespiga

import calespiga.config.{MqttConfig, OpenHabConfig, StatePersistenceConfig}
import calespiga.model.State
import calespiga.mqtt.{Consumer, Producer}
import calespiga.openhab.APIClient
import calespiga.persistence.StatePersistence
import cats.effect.{IO, Ref, ResourceIO}

trait ExternalInterfaces {
  def mqttConsumer(
      config: MqttConfig,
      topics: Set[String],
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[Consumer]

  def mqttProducer(
      config: MqttConfig,
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[Producer]

  def apiClient(
      config: OpenHabConfig,
      healthRestApi: HealthStatusManager.HealthComponentManager,
      healthWebSocket: HealthStatusManager.HealthComponentManager
  ): ResourceIO[APIClient]

  def statePersistence(
      config: StatePersistenceConfig,
      errorManager: ErrorManager,
      currentStateRef: Ref[IO, Option[State]],
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[StatePersistence]
}

object DefaultExternalInterfaces extends ExternalInterfaces {
  override def mqttConsumer(
      config: MqttConfig,
      topics: Set[String],
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[Consumer] =
    Consumer(config, topics, healthCheck)

  override def mqttProducer(
      config: MqttConfig,
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[Producer] =
    Producer(config, healthCheck)

  override def apiClient(
      config: OpenHabConfig,
      healthRestApi: HealthStatusManager.HealthComponentManager,
      healthWebSocket: HealthStatusManager.HealthComponentManager
  ): ResourceIO[APIClient] =
    APIClient(config, healthRestApi, healthWebSocket)

  override def statePersistence(
      config: StatePersistenceConfig,
      errorManager: ErrorManager,
      currentStateRef: Ref[IO, Option[State]],
      healthCheck: HealthStatusManager.HealthComponentManager
  ): ResourceIO[StatePersistence] =
    StatePersistence(config, errorManager, currentStateRef, healthCheck)
}
