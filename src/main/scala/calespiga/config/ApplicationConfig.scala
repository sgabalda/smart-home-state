package calespiga.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class ApplicationConfig(
    httpServerConfig: HttpServerConfig,
    mqttConfig: MqttConfig,
    uiConfig: UIConfig,
    powerProduction: PowerProductionConfig,
    statePersistenceConfig: StatePersistenceConfig,
    processor: ProcessorConfig
) derives ConfigReader

final case class HttpServerConfig(
    host: String,
    port: Int
) derives ConfigReader

final case class MqttConfig(
    host: String,
    port: Int,
    clientId: String,
    keepAlive: Int,
    cleanSession: Boolean,
    traceMessages: Boolean
) derives ConfigReader

final case class OpenHabConfig(
    host: String,
    port: Int,
    apiToken: String,
    retryDelay: FiniteDuration
) derives ConfigReader

final case class UIConfig(
    notificationsItem: String,
    defaultRepeatInterval: FiniteDuration,
    openHabConfig: OpenHabConfig
) derives ConfigReader

final case class StatePersistenceConfig(
    path: String,
    storePeriod: FiniteDuration
) derives ConfigReader

final case class ProcessorConfig(
    temperatureFans: TemperatureFansConfig,
    offlineDetector: OfflineDetectorConfig,
    syncDetector: SyncDetectorConfig,
    heater: HeaterConfig,
    featureFlags: FeatureFlagsConfig,
    power: PowerProcessorConfig
) derives ConfigReader

final case class PowerProcessorConfig(
    powerAvailable: PowerAvailableProcessorConfig,
    dynamicPower: DynamicPowerProcessorConfig
) derives ConfigReader

final case class PowerAvailableProcessorConfig(
    periodAlarmWithError: FiniteDuration,
    periodAlarmNoProduction: FiniteDuration,
    powerAvailableItem: String,
    powerProducedItem: String,
    powerDiscardedItem: String,
    readingsStatusItem: String
) derives ConfigReader

final case class DynamicPowerProcessorConfig(
    dynamicFVPowerUsedItem: String
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
    goalTemperatureItem: String,
    highTemperatureThreshold: Double,
    lowTemperatureThreshold: Double,
    thresholdNotificationPeriod: FiniteDuration
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
    lastCommandItem: String,
    syncTimeoutForDynamicPower: FiniteDuration,
    dynamicConsumerCode: String
) derives ConfigReader

final case class FeatureFlagsConfig(
    heaterMqttTopic: Set[String],
    setHeaterManagementItem: String
) derives ConfigReader

final case class PowerProductionConfig(
    sunnyBoy: SunnyBoyConfig,
    powerProductionSource: PowerProductionSourceConfig
) derives ConfigReader

final case class PowerProductionSourceConfig(
    pollingInterval: FiniteDuration,
    fvStartingHour: Int,
    fvEndingHour: Int
) derives ConfigReader

final case class SunnyBoyConfig(
    username: String,
    password: String,
    loginUrl: String,
    dataUrl: String,
    totalPowerCode: String,
    frequencyCode: String,
    linesCode: String,
    serialId: String,
    maxPowerAvailable: Float
) derives ConfigReader
