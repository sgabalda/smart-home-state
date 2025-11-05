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
    temperatureRelated: TemperatureFansConfig,
    offlineDetector: OfflineDetectorConfig,
    syncDetector: SyncDetectorConfig,
    heater: HeaterConfig,
    featureFlags: FeatureFlagsConfig
) derives ConfigReader

final case class TemperatureFansConfig(
    id: String,
    onlineStatusItem: String,
    temperaturesItems: TemperaturesItemsConfig,
    fansConfig: FansConfig
) derives ConfigReader

final case class TemperaturesItemsConfig(
    // OpenHAB item names for temperature readings
    batteryTemperatureItem: String,
    batteryClosetTemperatureItem: String,
    electronicsTemperatureItem: String,
    externalTemperatureItem: String,
    goalTemperatureItem: String
)

final case class FansConfig(
    resendInterval: FiniteDuration,
    timeoutInterval: FiniteDuration,
    // Fan control OpenHAB items
    batteryFanStatusItem: String,
    electronicsFanStatusItem: String,
    batteryFanInconsistencyItem: String,
    electronicsFanInconsistencyItem: String,
    // MQTT topics
    batteryFanMqttTopic: String,
    electronicsFanMqttTopic: String,
    // Internal IDs for action tracking
    batteryFanId: String,
    electronicsFanId: String
)

final case class OfflineDetectorConfig(
    timeoutDuration: FiniteDuration,
    onlineText: String,
    offlineText: String
) derives ConfigReader

final case class SyncDetectorConfig(
    timeoutDuration: FiniteDuration,
    syncText: String,
    syncingText: String,
    nonSyncText: String
) derives ConfigReader

final case class HeaterConfig(
    mqttTopicForCommand: String,
    statusItem: String,
    isHotItem: String,
    lastTimeHotItem: String,
    energyTodayItem: String,
    resendInterval: FiniteDuration,
    id: String,
    onlineStatusItem: String,
    syncStatusItem: String,
    lastCommandItem: String
) derives ConfigReader

final case class FeatureFlagsConfig(
    temperaturesMqttTopic: Set[String],
    setFanManagementItem: String,
    heaterMqttTopic: Set[String],
    setHeaterManagementItem: String
) derives ConfigReader
