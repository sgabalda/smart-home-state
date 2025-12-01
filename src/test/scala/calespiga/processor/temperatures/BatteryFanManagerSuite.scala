package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.config.BatteryFanConfig
import scala.concurrent.duration._
import calespiga.model.FanSignal
import com.softwaremill.quicklens._

class BatteryFanManagerSuite extends FunSuite {

  val dummyConfig = BatteryFanConfig(
    resendInterval = 10.seconds,
    batteryFanStatusItem = "BatteryFanStatusItem",
    batteryFanInconsistencyItem = "BatteryFanInconsistencyItem",
    batteryFanCommandItem = "BatteryFanCommandItem",
    batteryFanMqttTopic = "dummy/batteryFan",
    batteryFanId = "batteryFanId"
  )
  val manager = BatteryFanManager(dummyConfig)

  def getMqttCommand(actions: Set[Action], topic: String): Option[String] =
    actions.collectFirst { case Action.SendMqttStringMessage(`topic`, value) =>
      value
    }

  test("BatteryFanStatus updates state and emits correct action") {
    val status = FanSignal.Off
    val state = State()
    val event = calespiga.model.Event.Temperature.Fans.BatteryFanStatus(status)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteriesStatus, status)
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.batteryFanStatusItem,
          status.toString
        )
      )
    )
  }

  test("StartupEvent with On/Off commands sends correct MQTT actions") {
    val stateOn = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOn)
    val stateOff = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOff)
    val event = calespiga.model.Event.System.StartupEvent
    val (newStateOn, actionsOn) =
      manager.process(stateOn, event, java.time.Instant.now())
    val (newStateOff, actionsOff) =
      manager.process(stateOff, event, java.time.Instant.now())
    // On: should send "start" for both
    assertEquals(newStateOn, stateOn)
    assertEquals(
      getMqttCommand(actionsOn, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
    // Off: should send "stop" for both
    assertEquals(newStateOff, stateOff)
    assertEquals(
      getMqttCommand(actionsOff, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current between goal and external (above)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current between goal and external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current between goal and external (below)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(24.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(20.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current between goal and external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current not between goal and external (above)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current not between, should be On
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test(
    "StartupEvent with Automatic, current not between goal and external (below)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(26.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current not between, should be On
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test("StartupEvent with Automatic, missing current temperature") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(None)
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // missing current, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test("StartupEvent with Automatic, missing external temperature") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(None)
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // missing external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  // --- BatteryFanCommand tests ---
  test("BatteryFanCommand On, previous On: no action") {
    val state =
      State()
        .modify(_.fans.fanBatteriesLatestCommandReceived)
        .setTo(FanSignal.TurnOn)
        .modify(_.fans.fanBatteriesLatestCommandSent)
        .setTo(Some(FanSignal.On))
    val event =
      calespiga.model.Event.Temperature.Fans.BatteryFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.TurnOn
    )
    assertEquals(actions, Set.empty)
  }

  test("BatteryFanCommand Off, previous Off: no action") {
    val state =
      State()
        .modify(_.fans.fanBatteriesLatestCommandReceived)
        .setTo(FanSignal.TurnOff)
        .modify(_.fans.fanBatteriesLatestCommandSent)
        .setTo(Some(FanSignal.Off))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.TurnOff
    )
    assertEquals(actions, Set.empty)
  }

  test("BatteryFanCommand On, previous Off: actions sent, state updated") {
    val state =
      State()
        .modify(_.fans.fanBatteriesLatestCommandReceived)
        .setTo(FanSignal.TurnOff)
    val event =
      calespiga.model.Event.Temperature.Fans.BatteryFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.TurnOn
    )
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("BatteryFanCommand Off, previous On: actions sent, state updated") {
    val state =
      State()
        .modify(_.fans.fanBatteriesLatestCommandReceived)
        .setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.TurnOff
    )
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("BatteryFanCommand Automatic, previous Automatic: no action") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanBatteriesLatestCommandSent)
      .setTo(Some(FanSignal.Off))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.SetAutomatic
    )
    assertEquals(actions, Set.empty)
  }

  // BatteryFanCommand Automatic, previous not Automatic, various temps
  test(
    "BatteryFanCommand Automatic, previous Off, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOff)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "BatteryFanCommand Automatic, previous Off, current not between (above): On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOff)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test(
    "BatteryFanCommand Automatic, previous On, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(24.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(20.0))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "BatteryFanCommand Automatic, previous On, current not between (below): On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(26.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommandReceived,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- BatteryClosetTemperatureMeasured tests ---
  test(
    "BatteryClosetTemperatureMeasured, last command not automatic: no state change, no action"
  ) {
    val state =
      State()
        .modify(_.fans.fanBatteriesLatestCommandReceived)
        .setTo(FanSignal.TurnOn)
    val event =
      calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "BatteryClosetTemperatureMeasured, last command automatic, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val expectedState = state
      .modify(_.fans.fanBatteriesLatestCommandSent)
      .setTo(Some(FanSignal.Off))
    val event =
      calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, expectedState)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "BatteryClosetTemperatureMeasured, last command automatic, current not between (above): On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val expectedState = state
      .modify(_.fans.fanBatteriesLatestCommandSent)
      .setTo(Some(FanSignal.On))
    val event =
      calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured(25.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, expectedState)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- GoalTemperatureChanged tests ---
  test(
    "GoalTemperatureChanged, last commands not automatic: no state change, no action"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "GoalTemperatureChanged, batteries auto, current not between (above): On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val expectedState = state
      .modify(_.fans.fanBatteriesLatestCommandSent)
      .setTo(Some(FanSignal.On))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, expectedState)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test(
    "GoalTemperatureChanged, batteries not auto: no action"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assert(!getMqttCommand(actions, dummyConfig.batteryFanMqttTopic).isDefined)
  }

  test(
    "GoalTemperatureChanged, batteries auto current between: Off"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommandReceived)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val expectedState = state
      .modify(_.fans.fanBatteriesLatestCommandSent)
      .setTo(Some(FanSignal.Off))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, expectedState)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

}
