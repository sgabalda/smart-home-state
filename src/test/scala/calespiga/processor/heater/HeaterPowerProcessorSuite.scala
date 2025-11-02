package calespiga.processor.heater

import munit.FunSuite
import calespiga.model.{State, Action, Switch}
import calespiga.model.Event.Heater.*
import calespiga.model.HeaterSignal
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.config.HeaterConfig
import java.time.ZoneId

class HeaterPowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")

  private val zone: ZoneId = ZoneId.systemDefault()

  // Dummy HeaterConfig for processor instantiation
  private val dummyConfig = HeaterConfig(
    mqttTopicForCommand = "dummy/topic",
    lastTimeHotItem = "dummy/lastTimeHot",
    energyTodayItem = "dummy/energyToday",
    statusItem = "dummyStatusItem",
    isHotItem = "dummyIsHotItem",
    resendInterval = scala.concurrent.duration.DurationInt(20).seconds,
    id = "heater-processor",
    onlineStatusItem = "dummyOnlineStatusItem",
    syncStatusItem = "dummySyncStatusItem"
  )

  private def stateWithHeater(
      status: Option[HeaterSignal.ControllerState] = Some(HeaterSignal.Off),
      lastCommandSent: Option[HeaterSignal.ControllerState] = None,
      lastCommandReceived: Option[HeaterSignal.UserCommand] = None,
      lastChange: Option[Instant] = None,
      isHot: Switch.Status = Switch.Off,
      lastTimeHot: Option[Instant] = None,
      energyToday: Float = 0.0f
  ): State =
    State()
      .modify(_.heater)
      .setTo(
        State.Heater(
          status,
          lastCommandSent,
          lastCommandReceived,
          lastChange,
          isHot,
          lastTimeHot,
          energyToday
        )
      )

  test("HeaterPowerStatusReported accumulates energy if same day") {
    val secondsAgo = 3600
    val initialEnergy = 1000f
    val addedEnergy = 500f
    val oneHourAgo = now.minusSeconds(secondsAgo)
    val initialState = stateWithHeater(
      status = Some(HeaterSignal.Power500),
      lastChange = Some(oneHourAgo),
      energyToday = initialEnergy
    )
    val event = HeaterPowerStatusReported(HeaterSignal.Power1000)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(newState.heater.status, Some(HeaterSignal.Power1000))
    assertEquals(newState.heater.lastChange, Some(now))
    assertEqualsDouble(
      newState.heater.energyToday,
      initialEnergy + addedEnergy,
      0.1f
    )
    val expectedActions: Set[Action] = Set(
      Action.SetOpenHabItemValue(
        dummyConfig.energyTodayItem,
        newState.heater.energyToday.toString
      ),
      Action.SetOpenHabItemValue(
        dummyConfig.statusItem,
        HeaterSignal.Power1000.power.toString
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("HeaterPowerStatusReported resets energy if new day") {
    val initialEnergy = 10000f
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant
    val today = yesterday.plusSeconds(3600)
    val initialStateNewDay = stateWithHeater(
      status = Some(HeaterSignal.Power500),
      lastChange = Some(yesterday),
      energyToday = initialEnergy
    )
    val event = HeaterPowerStatusReported(HeaterSignal.Power1000)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState2, actions2) =
      processor.process(initialStateNewDay, event, today)
    assertEqualsDouble(
      newState2.heater.energyToday,
      500f,
      0.1f,
      "energyToday should reset for new day"
    )
    val expectedActions: Set[Action] = Set(
      Action.SetOpenHabItemValue(
        dummyConfig.energyTodayItem,
        newState2.heater.energyToday.toString
      ),
      Action.SetOpenHabItemValue(
        dummyConfig.statusItem,
        HeaterSignal.Power1000.power.toString
      )
    )
    assertEquals(actions2, expectedActions)
  }

  test(
    "HeaterPowerCommandChanged stores user and controller command, sends correct actions"
  ) {
    val initialState = stateWithHeater()
    val event = HeaterPowerCommandChanged(HeaterSignal.SetPower1000)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(
      newState.heater.lastCommandReceived,
      Some(HeaterSignal.SetPower1000)
    )
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power1000))
    val expectedActions = Set(
      Action
        .SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "1000"),
      Action.Periodic(
        dummyConfig.id + HeaterPowerProcessor.COMMAND_ACTION_SUFFIX,
        Action
          .SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "1000"),
        dummyConfig.resendInterval
      )
    )
    assertEquals(actions, expectedActions)
  }

  test(
    "HeaterIsHotReported(On) sets isHot, lastTimeHot, sends Off command and UI update"
  ) {
    val initialState = stateWithHeater(isHot = Switch.Off)
    val event = HeaterIsHotReported(Switch.On)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(newState.heater.isHot, Switch.On)
    assertEquals(newState.heater.lastTimeHot, Some(now))
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Off))
    val expectedActions = Set(
      Action.SendMqttStringMessage("dummy/topic", "0"),
      Action.Periodic(
        "heater-processor-command",
        Action.SendMqttStringMessage("dummy/topic", "0"),
        scala.concurrent.duration.DurationInt(20).seconds
      ),
      Action.SetOpenHabItemValue(dummyConfig.lastTimeHotItem, now.toString),
      Action.SetOpenHabItemValue(dummyConfig.isHotItem, true.toString)
    )
    assertEquals(actions, expectedActions)
  }

  test(
    "HeaterIsHotReported(Off) sets isHot, sends last user command and no UI update"
  ) {
    val initialState = stateWithHeater(
      isHot = Switch.On,
      lastCommandReceived = Some(HeaterSignal.SetPower500)
    )
    val event = HeaterIsHotReported(Switch.Off)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(newState.heater.isHot, Switch.Off)
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power500))
    val expectedActions = Set(
      Action.SendMqttStringMessage("dummy/topic", "500"),
      Action.Periodic(
        "heater-processor-command",
        Action.SendMqttStringMessage("dummy/topic", "500"),
        scala.concurrent.duration.DurationInt(20).seconds
      ),
      Action.SetOpenHabItemValue(dummyConfig.isHotItem, false.toString)
    )
    assertEquals(actions, expectedActions)
  }

  test("StartupEvent sends last user command or Off if none") {
    val initialState =
      stateWithHeater(lastCommandReceived = Some(HeaterSignal.SetPower2000))
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(
      initialState,
      calespiga.model.Event.System.StartupEvent,
      now
    )
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power2000))
    assertEquals(newState.heater.lastChange, Some(now))
    val expectedActions = Set(
      Action.SendMqttStringMessage("dummy/topic", "2000"),
      Action.Periodic(
        "heater-processor-command",
        Action.SendMqttStringMessage("dummy/topic", "2000"),
        scala.concurrent.duration.DurationInt(20).seconds
      )
    )
    assertEquals(actions, expectedActions)
  }
}
