package calespiga.config

import pureconfig.ConfigReader

final case class ApplicationConfig(
    mqttSourceConfig: MqttSourceConfig
) derives ConfigReader

final case class MqttSourceConfig(
    host: String,
    port: Int,
    clientId: String,
    keepAlive: Int,
    cleanSession: Boolean,
    traceMessages: Boolean,
    topics: List[String]
)
