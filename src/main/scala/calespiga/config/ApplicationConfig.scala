package calespiga.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class ApplicationConfig(
    mqttConfig: MqttConfig,
    openHabConfig: OpenHabConfig,
    statePersistenceConfig: StatePersistenceConfig
) derives ConfigReader

final case class MqttConfig(
    host: String,
    port: Int,
    clientId: String,
    keepAlive: Int,
    cleanSession: Boolean,
    traceMessages: Boolean
)

final case class OpenHabConfig(
    host: String,
    port: Int,
    apiToken: String
)

final case class StatePersistenceConfig(
    path: String,
    storePeriod: FiniteDuration
)
