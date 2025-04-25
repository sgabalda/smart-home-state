package calespiga.config

case class ApplicationConfig(
    mqttSourceConfig: MqttSourceConfig
)

case class MqttSourceConfig(
    host: String,
    port: Int,
    clientId: String,
    keepAlive: Int,
    cleanSession: Boolean,
    traceMessages: Boolean,
    topics: List[String]
)
