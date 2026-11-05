package calespiga.config

import pureconfig.ConfigReader
import scala.concurrent.duration.FiniteDuration

final case class ApplicationConfig(
    system: SystemConfig,
    httpServerConfig: HttpServerConfig,
    mqttConfig: MqttConfig,
    uiConfig: UIConfig,
    powerProduction: PowerProductionConfig,
    statePersistenceConfig: StatePersistenceConfig,
    processor: ProcessorConfig
) derives ConfigReader

final case class SystemConfig(
    timezone: String
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
    infraredStove: InfraredStoveConfig,
    greyWater: GreyWaterConfig,
    gardenWatering: GardenWateringConfig,
    pondPump: PondPumpConfig,
    featureFlags: FeatureFlagsConfig,
    power: PowerProcessorConfig,
    grid: GridConfig,
    carCharger: CarChargerConfig
) derives ConfigReader

final case class CarChargerConfig(
    statusItem: String,
    powerItem: String,
    energyTodayItem: String,
    chargingStatusItem: String,
    maxGridTariffItem: String,
    onlineStatusItem: String,
    syncStatusItem: String,
    offlineNotification: String,
    mqttTopicForCommand: String,
    resendInterval: FiniteDuration,
    lastCommandItem: String,
    id: String,
    syncTimeoutForDynamicPower: FiniteDuration,
    automaticOnGracePeriod: FiniteDuration,
    dynamicConsumerCode: String,
    chargerPowerWatts: Float
) derives ConfigReader

final case class PowerProcessorConfig(
    powerAvailable: PowerAvailableProcessorConfig,
    energyToday: PowerEnergyTodayConfig,
    dynamicPower: DynamicPowerProcessorConfig
) derives ConfigReader

final case class PowerEnergyTodayConfig(
    homeConsumptionItem: String,
    gridConsumedItem: String,
    gridInjectedItem: String,
    gridConsumedVallItem: String,
    gridConsumedPlaItem: String,
    gridConsumedPicItem: String,
    gridInjectedVallItem: String,
    gridInjectedPlaItem: String,
    gridInjectedPicItem: String
) derives ConfigReader

final case class PowerAvailableProcessorConfig(
    periodAlarmWithError: FiniteDuration,
    periodAlarmNoProduction: FiniteDuration,
    powerAvailableItem: String,
    powerProducedItem: String,
    powerDiscardedItem: String,
    homeConsumptionItem: String,
    powerFromNetworkItem: String,
    readingsStatusItem: String
) derives ConfigReader

final case class DynamicPowerProcessorConfig(
    dynamicFVPowerUsedItem: String,
    debouncePeriod: FiniteDuration
) derives ConfigReader

final case class TemperatureFansConfig(
    id: String,
    onlineStatusItem: String,
    temperaturesItems: TemperaturesItemsConfig,
    fans: FansConfig
) derives ConfigReader

final case class TemperaturesItemsConfig(
    // OpenHAB item names for temperature readings
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
    dynamicConsumerCode: String,
    temperatureItem: String,
    goalTemperatureItem: String
) derives ConfigReader

final case class InfraredStoveConfig(
    mqttTopicForCommand: String,
    statusItem: String,
    energyTodayItem: String,
    resendInterval: FiniteDuration,
    id: String,
    onlineStatusItem: String,
    syncStatusItem: String,
    lastCommandItem: String,
    lastChangeItem: String,
    syncTimeoutForDynamicPower: FiniteDuration,
    dynamicConsumerCode: String,
    programmedOffTimeItem: String
) derives ConfigReader

final case class GreyWaterConfig(
    mqttTopicForCommand: String,
    resendInterval: FiniteDuration,
    id: String,
    statusItem: String,
    modeItem: String,
    startHourItem: String,
    endHourItem: String,
    scheduleDescriptionItem: String,
    syncStatusItem: String,
    onlineStatusItem: String
) derives ConfigReader

final case class GardenWateringConfig(
    pumpId: String,
    manualDurationItem: String,
    manualStartItem: String,
    scheduledDurationItem: String,
    periodHoursItem: String,
    startHourItem: String,
    lastWateringItem: String
) derives ConfigReader

final case class PondPumpConfig(
    id: String,
    mqttTopicForCommand: String,
    resendInterval: FiniteDuration,
    statusItem: String,
    syncStatusItem: String,
    onlineStatusItem: String
) derives ConfigReader

final case class FeatureFlagsConfig(
    greyWaterMqttTopic: Set[String],
    setGreyWaterEnabledItem: String,
    gardenWateringMqttTopic: Set[String],
    setGardenWateringEnabledItem: String
) derives ConfigReader

final case class GridConfig(
    tariffItem: String,
    availablePower: Float // as there is the same power currently available for all the grid tariffs, we can configure it here
) derives ConfigReader

final case class PowerProductionConfig(
    sunnyBoy: SunnyBoyConfig
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
