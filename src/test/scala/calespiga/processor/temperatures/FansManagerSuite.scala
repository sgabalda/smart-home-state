package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.config.FansConfig
import scala.concurrent.duration._
import calespiga.model.FanSignal
import com.softwaremill.quicklens._

class FansManagerSuite extends FunSuite {

  val dummyConfig = FansConfig(
    resendInterval = 10.seconds,
    timeoutInterval = 30.seconds,
    batteryFanStatusItem = "BatteryFanStatusItem",
    electronicsFanStatusItem = "ElectronicsFanStatusItem",
    batteryFanInconsistencyItem = "BatteryFanInconsistencyItem",
    electronicsFanInconsistencyItem = "ElectronicsFanInconsistencyItem",
    batteryFanCommandItem = "BatteryFanCommandItem",
    electronicsFanCommandItem = "ElectronicsFanCommandItem",
    batteryFanMqttTopic = "dummy/batteryFan",
    electronicsFanMqttTopic = "dummy/electronicsFan",
    batteryFanId = "batteryFanId",
    electronicsFanId = "electronicsFanId"
  )
  val manager = FansManager(dummyConfig)

  def getMqttCommand(actions: Set[Action], topic: String): Option[String] =
    actions.collectFirst { case Action.SendMqttStringMessage(`topic`, value) =>
      value
    }

  test("ElectronicsFanStatus updates state and emits correct action") {
    val status = FanSignal.On
    val state = State()
    val event =
      calespiga.model.Event.Temperature.Fans.ElectronicsFanStatus(status)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanElectronics, status)
    assertEquals(
      actions,
      Set[Action](
        Action.SetOpenHabItemValue(
          dummyConfig.electronicsFanStatusItem,
          status.toString
        )
      )
    )
  }

  test("BatteryFanStatus updates state and emits correct action") {
    val status = FanSignal.Off
    val state = State()
    val event = calespiga.model.Event.Temperature.Fans.BatteryFanStatus(status)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteries, status)
    assertEquals(
      actions,
      Set[Action](
        Action.SetOpenHabItemValue(
          dummyConfig.batteryFanStatusItem,
          status.toString
        )
      )
    )
  }

  test("StartupEvent with On/Off commands sends correct MQTT actions") {
    val stateOn = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOn)
    val stateOff = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.TurnOff)
      .modify(_.fans.fanElectronicsLatestCommand)
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
    assertEquals(
      getMqttCommand(actionsOn, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
    // Off: should send "stop" for both
    assertEquals(newStateOff, stateOff)
    assertEquals(
      getMqttCommand(actionsOff, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actionsOff, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current between goal and external (above)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current between goal and external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current between goal and external (below)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(24.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(20.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current between goal and external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "StartupEvent with Automatic, current not between goal and external (above)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(25.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current not between, should be On
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test(
    "StartupEvent with Automatic, current not between goal and external (below)"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(26.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // current not between, should be On
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test("StartupEvent with Automatic, missing current temperature") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(None)
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // missing current, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test("StartupEvent with Automatic, missing external temperature") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(None)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.System.StartupEvent
    val (_, actions) = manager.process(state, event, java.time.Instant.now())
    // missing external, should be Off
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  // --- BatteryFanCommand tests ---
  test("BatteryFanCommand On, previous On: no action") {
    val state =
      State().modify(_.fans.fanBatteriesLatestCommand).setTo(FanSignal.TurnOn)
    val event =
      calespiga.model.Event.Temperature.Fans.BatteryFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteriesLatestCommand, FanSignal.TurnOn)
    assertEquals(actions, Set.empty)
  }

  test("BatteryFanCommand Off, previous Off: no action") {
    val state =
      State().modify(_.fans.fanBatteriesLatestCommand).setTo(FanSignal.TurnOff)
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteriesLatestCommand, FanSignal.TurnOff)
    assertEquals(actions, Set.empty)
  }

  test("BatteryFanCommand On, previous Off: actions sent, state updated") {
    val state =
      State().modify(_.fans.fanBatteriesLatestCommand).setTo(FanSignal.TurnOff)
    val event =
      calespiga.model.Event.Temperature.Fans.BatteryFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteriesLatestCommand, FanSignal.TurnOn)
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("BatteryFanCommand Off, previous On: actions sent, state updated") {
    val state =
      State().modify(_.fans.fanBatteriesLatestCommand).setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanBatteriesLatestCommand, FanSignal.TurnOff)
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("BatteryFanCommand Automatic, previous Automatic: no action") {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
    val event = calespiga.model.Event.Temperature.Fans
      .BatteryFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanBatteriesLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(actions, Set.empty)
  }

  // BatteryFanCommand Automatic, previous not Automatic, various temps
  test(
    "BatteryFanCommand Automatic, previous Off, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
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
      newState.fans.fanBatteriesLatestCommand,
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
      .modify(_.fans.fanBatteriesLatestCommand)
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
      newState.fans.fanBatteriesLatestCommand,
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
      .modify(_.fans.fanBatteriesLatestCommand)
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
      newState.fans.fanBatteriesLatestCommand,
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
      .modify(_.fans.fanBatteriesLatestCommand)
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
      newState.fans.fanBatteriesLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- ElectronicsFanCommand tests ---
  test("ElectronicsFanCommand On, previous On: no action") {
    val state =
      State().modify(_.fans.fanElectronicsLatestCommand).setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanElectronicsLatestCommand, FanSignal.TurnOn)
    assertEquals(actions, Set.empty)
  }

  test("ElectronicsFanCommand Off, previous Off: no action") {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOff)
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanElectronicsLatestCommand, FanSignal.TurnOff)
    assertEquals(actions, Set.empty)
  }

  test("ElectronicsFanCommand On, previous Off: actions sent, state updated") {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOff)
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.TurnOn)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanElectronicsLatestCommand, FanSignal.TurnOn)
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("ElectronicsFanCommand Off, previous On: actions sent, state updated") {
    val state =
      State().modify(_.fans.fanElectronicsLatestCommand).setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.TurnOff)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState.fans.fanElectronicsLatestCommand, FanSignal.TurnOff)
    assert(actions.exists(_.isInstanceOf[Action.SendMqttStringMessage]))
    assert(actions.exists(_.isInstanceOf[Action.Periodic]))
  }

  test("ElectronicsFanCommand Automatic, previous Automatic: no action") {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanElectronicsLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(actions, Set.empty)
  }

  // ElectronicsFanCommand Automatic, previous not Automatic, various temps
  test(
    "ElectronicsFanCommand Automatic, previous Off, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOff)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanElectronicsLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "ElectronicsFanCommand Automatic, previous Off, current not between (above): On"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOff)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanElectronicsLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  test(
    "ElectronicsFanCommand Automatic, previous On, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(24.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(20.0))
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanElectronicsLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "ElectronicsFanCommand Automatic, previous On, current not between (below): On"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(26.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.Fans
      .ElectronicsFanCommand(FanSignal.SetAutomatic)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(
      newState.fans.fanElectronicsLatestCommand,
      FanSignal.SetAutomatic
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- BatteryClosetTemperatureMeasured tests ---
  test(
    "BatteryClosetTemperatureMeasured, last command not automatic: no state change, no action"
  ) {
    val state =
      State().modify(_.fans.fanBatteriesLatestCommand).setTo(FanSignal.TurnOn)
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
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event =
      calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "BatteryClosetTemperatureMeasured, last command automatic, current not between (above): On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event =
      calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured(25.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- ElectronicsTemperatureMeasured tests ---
  test(
    "ElectronicsTemperatureMeasured, last command not automatic: no state change, no action"
  ) {
    val state =
      State().modify(_.fans.fanElectronicsLatestCommand).setTo(FanSignal.TurnOn)
    val event =
      calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "ElectronicsTemperatureMeasured, last command automatic, current between goal and external: Off"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event =
      calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
  }

  test(
    "ElectronicsTemperatureMeasured, last command automatic, current not between (below): On"
  ) {
    val state = State()
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(26.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event =
      calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

  // --- GoalTemperatureChanged tests ---
  test(
    "GoalTemperatureChanged, last commands not automatic: no state change, no action"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOn)
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(22.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }

  test(
    "GoalTemperatureChanged, batteries auto, current not between (above): On; electronics not auto: no action"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
    assert(
      !getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic).isDefined
    )
  }

  test(
    "GoalTemperatureChanged, electronics auto, current between goal and external: Off; batteries not auto: no action"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.TurnOn)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(24.0))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assert(!getMqttCommand(actions, dummyConfig.batteryFanMqttTopic).isDefined)
  }

  test(
    "GoalTemperatureChanged, both auto, batteries current between: Off, electronics not between: On"
  ) {
    val state = State()
      .modify(_.fans.fanBatteriesLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.fans.fanElectronicsLatestCommand)
      .setTo(FanSignal.SetAutomatic)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0)
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(Some(22.0))
      .modify(_.temperatures.electronicsTemperature)
      .setTo(Some(25.0))
      .modify(_.temperatures.externalTemperature)
      .setTo(Some(22.0))
    val event = calespiga.model.Event.Temperature.GoalTemperatureChanged(20.0)
    val (newState, actions) =
      manager.process(state, event, java.time.Instant.now())
    assertEquals(newState, state)
    assertEquals(
      getMqttCommand(actions, dummyConfig.batteryFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.Off))
    )
    assertEquals(
      getMqttCommand(actions, dummyConfig.electronicsFanMqttTopic),
      Some(FanSignal.controllerStateToCommand(FanSignal.On))
    )
  }

}
