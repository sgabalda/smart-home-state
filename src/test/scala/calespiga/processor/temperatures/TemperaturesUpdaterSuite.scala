package calespiga.processor.temperatures

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import java.time.Instant
import com.softwaremill.quicklens.*
import scala.concurrent.duration.*
import calespiga.processor.ProcessorConfigHelper

class TemperaturesUpdaterSuite extends FunSuite {

  val dummyConfig = ProcessorConfigHelper.temperaturesItemsConfig
  val updater = TemperaturesUpdater(dummyConfig)
  val now = Instant.parse("2023-08-17T10:00:00Z")

  test("SystemStartup emits action to set goal temperature if present") {
    val temp = 21.5
    val state = State().modify(_.temperatures.goalTemperature).setTo(temp)
    val event = Event.System.StartupEvent
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState, state)
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.externalTemperatureItem,
          temp.toString
        )
      )
    )
  }

  test("GoalTemperatureChanged updates state and emits no action") {
    val temp = 22.2
    val state = State()
    val event = Event.Temperature.GoalTemperatureChanged(temp)
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState.temperatures.goalTemperature, temp)
    assertEquals(actions, Set.empty)
  }

  test("BatteryTemperatureMeasured updates state and emits action") {
    val temp = 21.5
    val state = State()
    val event = Event.Temperature.BatteryTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState.temperatures.batteriesTemperature, Some(temp))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.batteryTemperatureItem,
          temp.toString
        )
      )
    )
  }

  test("BatteryClosetTemperatureMeasured updates state and emits action") {
    val temp = 19.0
    val state = State()
    val event = Event.Temperature.BatteryClosetTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState.temperatures.batteriesClosetTemperature, Some(temp))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.batteryClosetTemperatureItem,
          temp.toString
        )
      )
    )
  }

  test("ElectronicsTemperatureMeasured updates state and emits action") {
    val temp = 28.3
    val state = State()
    val event = Event.Temperature.ElectronicsTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState.temperatures.electronicsTemperature, Some(temp))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.electronicsTemperatureItem,
          temp.toString
        )
      )
    )
  }

  test(
    "BatteryTemperatureMeasured emits notification if under lower threshold"
  ) {
    val temp = 4.0
    val state = State()
    val event = Event.Temperature.BatteryTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assert(actions.find {
      case Action.SendNotification(id, msg, repeat)
          if id.endsWith(
            TemperaturesUpdater.LOW_TEMPERATURE_NOTIFICATION_ID_SUFFIX
          )
            && msg.contains("Bateria")
            && repeat.contains(2.hours) =>
        true
      case _ => false
    }.isDefined)

  }

  test(
    "BatteryTemperatureMeasured emits notification if above high threshold"
  ) {
    val temp = 44.0
    val state = State()
    val event = Event.Temperature.BatteryTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assert(actions.find {
      case Action.SendNotification(id, msg, repeat)
          if id.endsWith(
            TemperaturesUpdater.HIGH_TEMPERATURE_NOTIFICATION_ID_SUFFIX
          )
            && msg.contains("Bateria")
            && repeat.contains(2.hours) =>
        true
      case _ => false
    }.isDefined)
  }

  test(
    "ElectronicsTemperatureMeasured emits notification if under lower threshold"
  ) {
    val temp = 4.0
    val state = State()
    val event = Event.Temperature.ElectronicsTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assert(actions.find {
      case Action.SendNotification(id, msg, repeat)
          if id.endsWith(
            TemperaturesUpdater.LOW_TEMPERATURE_NOTIFICATION_ID_SUFFIX
          )
            && msg.contains("Electrònica")
            && repeat.contains(2.hours) =>
        true
      case _ => false
    }.isDefined)

  }

  test(
    "ElectronicsTemperatureMeasured emits notification if above high threshold"
  ) {
    val temp = 44.0
    val state = State()
    val event = Event.Temperature.ElectronicsTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assert(actions.find {
      case Action.SendNotification(id, msg, repeat)
          if id.endsWith(
            TemperaturesUpdater.HIGH_TEMPERATURE_NOTIFICATION_ID_SUFFIX
          )
            && msg.contains("Electrònica")
            && repeat.contains(2.hours) =>
        true
      case _ => false
    }.isDefined)
  }

  test("ExternalTemperatureMeasured updates state and emits action") {
    val temp = 15.7
    val state = State()
    val event = Event.Temperature.ExternalTemperatureMeasured(temp)
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState.temperatures.externalTemperature, Some(temp))
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          dummyConfig.externalTemperatureItem,
          temp.toString
        )
      )
    )
  }

  test("Unrelated event does not update state or emit action") {
    val state = State()
    val event = Event.Heater.HeaterPowerStatusReported(
      calespiga.model.HeaterSignal.Power500
    )
    val (newState, actions) = updater.process(state, event, now)
    assertEquals(newState, state)
    assertEquals(actions, Set.empty)
  }
}
