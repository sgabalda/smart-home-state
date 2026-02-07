package calespiga.processor

import munit.CatsEffectSuite
import cats.effect.IO
import com.softwaremill.quicklens.*
import scala.concurrent.duration.DurationInt
import calespiga.config.*
import java.time.ZoneId
import cats.effect.Ref

class StateProcessorSuite extends CatsEffectSuite {
  import calespiga.model.{State, Action, Event}
  import java.time.Instant

  val now = Instant.parse("2023-08-17T10:00:00Z")
  val event = Event.Temperature.BatteryTemperatureMeasured(42.0)

  val initialState =
    State().modify(_.temperatures.batteriesTemperature).setTo(Some(0d))
  val action1 = Action.SetUIItemValue("item1", "value1")
  val action2 = Action.SetUIItemValue("item2", "value2")
  // Dummy processors
  val processor1 = new EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      if (eventData != event) fail("Event not correct")
      val newState =
        state.modify(_.temperatures.batteriesTemperature).setTo(Some(10d))
      IO.pure((newState, Set(action1)))
    }
  }
  val processor2 = new EffectfulProcessor {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      if (eventData != event) fail("Event not correct")
      val newState =
        state.modify(_.temperatures.batteriesTemperature).using(_.map(_ + 5))
      IO.pure((newState, Set(action2)))
    }
  }

  test("Events are forwarded to all the processors") {

    val stateProcessor = StateProcessor(processor1, processor2)

    stateProcessor.process(initialState, Event(now, event)).map {
      case (_, actions) =>
        // Both processors should have received the event and produced their actions
        assertEquals(actions, Set[Action](action1, action2))
    }
  }

  test("the State is modified by all the processors, in the given order ") {
    val stateProcessor = StateProcessor(processor1, processor2)

    stateProcessor.process(initialState, Event(now, event)).map {
      case (finalState, actions) =>
        // State should reflect both changes, in order
        assertEquals(finalState.temperatures.batteriesTemperature, Some(15d))
    }
  }

  test("configured dynamic consumers do not have duplicate codes") {
    Ref.of[IO, Set[String]](Set.empty[String]).map { mqttBlacklist =>
      val allDynConsumers = StateProcessor
        .allButPower(
          config = calespiga.config.ProcessorConfig(
            temperatureFans = TemperatureFansConfig(
              id = "temperature-fans-processor",
              onlineStatusItem = "temperature-fans/onlineStatus",
              temperaturesItems = TemperaturesItemsConfig(
                batteryTemperatureItem =
                  "temperature-fans/batteriesTemperature",
                batteryClosetTemperatureItem =
                  "temperature-fans/batteryClosetTemperature",
                electronicsTemperatureItem =
                  "temperature-fans/electronicsTemperature",
                externalTemperatureItem =
                  "temperature-fans/externalTemperature",
                goalTemperatureItem = "temperature-fans/goalTemperature",
                highTemperatureThreshold = 30d,
                lowTemperatureThreshold = 15d,
                thresholdNotificationPeriod = 10.minutes
              ),
              fans = FansConfig(
                batteryFan = BatteryFanConfig(
                  batteryFanStatusItem = "batteryFan/status",
                  batteryFanInconsistencyItem = "batteryFan/inconsistency",
                  batteryFanCommandItem = "batteryFan/command",
                  batteryFanMqttTopic = "batteryFan/mqttTopic",
                  batteryFanId = "battery-fan-consumer-code",
                  resendInterval = 30.seconds
                ),
                electronicsFan = ElectronicsFanConfig(
                  electronicsFanStatusItem = "electronicsFan/status",
                  electronicsFanInconsistencyItem =
                    "electronicsFan/inconsistency",
                  electronicsFanCommandItem = "electronicsFan/command",
                  electronicsFanMqttTopic = "electronicsFan/mqttTopic",
                  electronicsFanId = "shared-consumer-code",
                  resendInterval = 30.seconds
                )
              )
            ),
            offlineDetector = calespiga.config.OfflineDetectorConfig(
              timeoutDuration = 15.minutes,
              onlineText = "Online",
              offlineText = "Offline"
            ),
            syncDetector = calespiga.config.SyncDetectorConfig(
              timeoutDuration = 5.minutes,
              syncText = "In Sync",
              syncingText = "Syncing",
              nonSyncText = "No Sync"
            ),
            heater = calespiga.config.HeaterConfig(
              mqttTopicForCommand = "heater/command",
              lastTimeHotItem = "heater/lastTimeHot",
              energyTodayItem = "heater/energyToday",
              statusItem = "heater/status",
              isHotItem = "heater/isHot",
              resendInterval = 20.seconds,
              id = "heater-processor",
              onlineStatusItem = "heater/onlineStatus",
              syncStatusItem = "heater/syncStatus",
              lastCommandItem = "heater/lastCommand",
              syncTimeoutForDynamicPower = 10.seconds,
              dynamicConsumerCode = "shared-consumer-code"
            ),
            featureFlags = calespiga.config.FeatureFlagsConfig(
              heaterMqttTopic = Set.empty,
              setHeaterManagementItem = "featureFlags/setHeaterManagement"
            ),
            power = calespiga.config.PowerProcessorConfig(
              powerAvailable = PowerAvailableProcessorConfig(
                periodAlarmWithError = 10.minutes,
                periodAlarmNoProduction = 15.minutes,
                powerAvailableItem = "power/powerAvailable",
                powerProducedItem = "power/powerProduced",
                powerDiscardedItem = "power/powerDiscarded",
                readingsStatusItem = "power/readingsStatus"
              ),
              dynamicPower = DynamicPowerProcessorConfig(
                dynamicFVPowerUsedItem = "dynamicFVPowerUsed"
              )
            )
          ),
          mqttBlacklist = mqttBlacklist,
          zoneId = ZoneId.systemDefault()
        )
        .flatMap(_.dynamicPowerConsumer)
        .toList
        .map(_.uniqueCode)

      assertEquals(
        allDynConsumers.size,
        allDynConsumers.toSet.size,
        "There are duplicate dynamic consumer codes: " + allDynConsumers
      )
    }
  }
}
