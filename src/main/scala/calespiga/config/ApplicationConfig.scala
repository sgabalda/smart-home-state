package calespiga.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class ApplicationConfig(
    mqttConfig: MqttConfig,
    openHabConfig: OpenHabConfig,
    statePersistenceConfig: StatePersistenceConfig,
    processor: ProcessorConfig
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

final case class ProcessorConfig(
    temperatureRelated: TemperatureRelatedConfig,
    offlineDetector: OfflineDetectorConfig
) derives ConfigReader

final case class TemperatureRelatedConfig(
    resendInterval: FiniteDuration,
    timeoutInterval: FiniteDuration,
    // OpenHAB item names for temperature readings
    batteryTemperatureItem: String,
    batteryClosetTemperatureItem: String,
    electronicsTemperatureItem: String,
    externalTemperatureItem: String,
    // Fan control OpenHAB items
    batteryFanStatusItem: String,
    batteryFanCommandItem: String,
    electronicsFanStatusItem: String,
    electronicsFanCommandItem: String,
    fansInconsistencyItem: String,
    // MQTT topics
    batteryFanMqttTopic: String,
    electronicsFanMqttTopic: String,
    // Internal IDs for action tracking
    batteryFanId: String,
    electronicsFanId: String
) derives ConfigReader

final case class OfflineDetectorConfig(
    timeoutDuration: FiniteDuration,
    temperaturesStatusItem: String,
    onlineText: String,
    offlineText: String
) derives ConfigReader
