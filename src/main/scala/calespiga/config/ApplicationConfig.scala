package calespiga.config

import pureconfig.ConfigReader

final case class ApplicationConfig(
    mqttConfig: MqttConfig,
    openHabConfig: OpenHabConfig
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
