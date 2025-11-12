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
    temperatureFans: TemperatureFansConfig,
    offlineDetector: OfflineDetectorConfig,
    syncDetector: SyncDetectorConfig,
    heater: HeaterConfig,
    featureFlags: FeatureFlagsConfig
) derives ConfigReader

final case class TemperatureFansConfig(
    id: String,
    onlineStatusItem: String,
    temperaturesItems: TemperaturesItemsConfig,
    fans: FansConfig
) derives ConfigReader

final case class TemperaturesItemsConfig(
    // OpenHAB item names for temperature readings
    batteryTemperatureItem: String,
    batteryClosetTemperatureItem: String,
    electronicsTemperatureItem: String,
    externalTemperatureItem: String,
    goalTemperatureItem: String
) derives ConfigReader

final case class FansConfig(
    batteryFan: BatteryFanConfig,
    electronicsFan: ElectronicsFanConfig
) derives ConfigReader

final case class BatteryFanConfig(
    batteryFanStatusItem: String,
    batteryFanInconsistencyItem: String,
    batteryFanCommandItem: String,
    batteryFanMqttTopic: String,
    batteryFanId: String,
    resendInterval: FiniteDuration
) derives ConfigReader

final case class ElectronicsFanConfig(
    electronicsFanStatusItem: String,
    electronicsFanInconsistencyItem: String,
    electronicsFanCommandItem: String,
    electronicsFanMqttTopic: String,
    electronicsFanId: String,
    resendInterval: FiniteDuration
) derives ConfigReader

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
