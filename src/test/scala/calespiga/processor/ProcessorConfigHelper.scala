package calespiga.processor

import calespiga.config.*
import scala.concurrent.duration.DurationInt

object ProcessorConfigHelper {

  val temperaturesItemsConfig: TemperaturesItemsConfig =
    TemperaturesItemsConfig(
      batteryTemperatureItem = "BatteryTempItem",
      batteryClosetTemperatureItem = "BatteryClosetTempItem",
      electronicsTemperatureItem = "ElectronicsTempItem",
      externalTemperatureItem = "ExternalTempItem",
      goalTemperatureItem = "DummyGoalTempItem",
      highTemperatureThreshold = 30.0,
      lowTemperatureThreshold = 5.0,
      thresholdNotificationPeriod = 2.hours
    )

  val batteryFanConfig: BatteryFanConfig = BatteryFanConfig(
    batteryFanStatusItem = "BatteryFanStatusItem",
    batteryFanInconsistencyItem = "BatteryFanInconsistencyItem",
    batteryFanCommandItem = "BatteryFanCommandItem",
    batteryFanMqttTopic = "dummy/batteryFan",
    batteryFanId = "batteryFanId",
    resendInterval = 10.seconds
  )

  val electronicsFanConfig: ElectronicsFanConfig = ElectronicsFanConfig(
    electronicsFanStatusItem = "ElectronicsFanStatusItem",
    electronicsFanInconsistencyItem = "ElectronicsFanInconsistencyItem",
    electronicsFanCommandItem = "ElectronicsFanCommandItem",
    electronicsFanMqttTopic = "dummy/electronicsFan",
    electronicsFanId = "electronicsFanId",
    resendInterval = 10.seconds
  )

  val fansConfig: FansConfig = FansConfig(
    batteryFan = batteryFanConfig,
    electronicsFan = electronicsFanConfig
  )

  val temperatureFansConfig: TemperatureFansConfig = TemperatureFansConfig(
    id = "temperature-fans-processor",
    onlineStatusItem = "temperature-fans/onlineStatus",
    temperaturesItems = temperaturesItemsConfig,
    fans = fansConfig
  )

  val offlineDetectorConfig: OfflineDetectorConfig = OfflineDetectorConfig(
    timeoutDuration = 30.seconds,
    onlineText = "ONLINE",
    offlineText = "OFFLINE"
  )

  val syncDetectorConfig: SyncDetectorConfig = SyncDetectorConfig(
    timeoutDuration = 30.seconds,
    syncText = "SYNC",
    syncingText = "SYNCING",
    nonSyncText = "NON_SYNC"
  )

  val heaterConfig: HeaterConfig = HeaterConfig(
    mqttTopicForCommand = "dummy/topic",
    lastTimeHotItem = "dummy/lastTimeHot",
    energyTodayItem = "dummy/energyToday",
    statusItem = "dummyStatusItem",
    isHotItem = "dummyIsHotItem",
    resendInterval = 20.seconds,
    id = "heater-processor",
    onlineStatusItem = "dummyOnlineStatusItem",
    syncStatusItem = "dummySyncStatusItem",
    lastCommandItem = "dummyLastCommandItem",
    syncTimeoutForDynamicPower = 50.seconds,
    dynamicConsumerCode = "heater-consumer-code"
  )

  val infraredStoveConfig: InfraredStoveConfig = InfraredStoveConfig(
    mqttTopicForCommand = "infraredStove/command",
    statusItem = "infraredStove/status",
    energyTodayItem = "infraredStove/energyToday",
    resendInterval = 20.seconds,
    id = "infrared-stove-processor",
    onlineStatusItem = "infraredStove/onlineStatus",
    syncStatusItem = "infraredStove/syncStatus",
    lastCommandItem = "infraredStove/lastCommand"
  )

  val featureFlagsConfig: FeatureFlagsConfig = FeatureFlagsConfig(
    heaterMqttTopic = Set("heater/topic1", "heater/topic2"),
    setHeaterManagementItem = "featureFlags/setHeaterManagement"
  )

  val powerAvailableProcessorConfig: PowerAvailableProcessorConfig =
    PowerAvailableProcessorConfig(
      periodAlarmWithError = 10.minutes,
      periodAlarmNoProduction = 15.minutes,
      powerAvailableItem = "power/powerAvailable",
      powerProducedItem = "power/powerProduced",
      powerDiscardedItem = "power/powerDiscarded",
      readingsStatusItem = "power/readingsStatus"
    )

  val dynamicPowerProcessorConfig: DynamicPowerProcessorConfig =
    DynamicPowerProcessorConfig(
      dynamicFVPowerUsedItem = "dynamicFVPowerUsed"
    )

  val powerProcessorConfig: PowerProcessorConfig = PowerProcessorConfig(
    powerAvailable = powerAvailableProcessorConfig,
    dynamicPower = dynamicPowerProcessorConfig
  )

  val processorConfig: ProcessorConfig = ProcessorConfig(
    temperatureFans = temperatureFansConfig,
    offlineDetector = offlineDetectorConfig,
    syncDetector = syncDetectorConfig,
    heater = heaterConfig,
    infraredStove = infraredStoveConfig,
    featureFlags = featureFlagsConfig,
    power = powerProcessorConfig
  )
}
