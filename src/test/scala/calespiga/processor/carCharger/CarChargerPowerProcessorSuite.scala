package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{Action, CarChargerSignal}
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions
import CarChargerTestHelper.stateWithCarCharger

class CarChargerPowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-15T10:00:00Z")

  private val config = ProcessorConfigHelper.carCharger

  test(
    "CarChargerPowerCommandChanged stores user and controller command, sends correct actions"
  ) {
    val initialState = stateWithCarCharger()
    val event = CarChargerPowerCommandChanged(CarChargerSignal.TurnOn)
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(
      newState.carCharger.lastCommandReceived,
      Some(CarChargerSignal.TurnOn)
    )
    assertEquals(newState.carCharger.lastCommandSent, Some(CarChargerSignal.On))

    val expectedActions = Set(
      Action.SendMqttStringMessage(config.mqttTopicForCommand, "on"),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(config.mqttTopicForCommand, "on"),
        config.resendInterval
      )
    )

    assertEquals(actions, expectedActions)
  }

  test(
    "CarChargerPowerCommandChanged SetAutomaticFV sends Off to microcontroller"
  ) {
    val initialState = stateWithCarCharger()
    val event = CarChargerPowerCommandChanged(CarChargerSignal.SetAutomaticFV)
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(
      newState.carCharger.lastCommandReceived,
      Some(CarChargerSignal.SetAutomaticFV)
    )
    assertEquals(
      newState.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )

    val expectedActions = Set(
      Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
        config.resendInterval
      )
    )

    assertEquals(actions, expectedActions)
  }

  test(
    "CarChargerPowerCommandChanged SetAutomaticGrid sends Off to microcontroller"
  ) {
    val initialState = stateWithCarCharger()
    val event = CarChargerPowerCommandChanged(CarChargerSignal.SetAutomaticGrid)
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(
      newState.carCharger.lastCommandReceived,
      Some(CarChargerSignal.SetAutomaticGrid)
    )
    assertEquals(
      newState.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )

    val expectedActions = Set(
      Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
        config.resendInterval
      )
    )

    assertEquals(actions, expectedActions)
  }

  test("SetAutomaticGrid converts to and from its command string") {
    assertEquals(
      CarChargerSignal.userCommandToString(CarChargerSignal.SetAutomaticGrid),
      "automatic_grid"
    )
    assertEquals(
      CarChargerSignal.userCommandFromString("automatic_grid"),
      Right(CarChargerSignal.SetAutomaticGrid)
    )
  }

  test("StartupEvent sends last user command or Off if none") {
    val initialState =
      stateWithCarCharger(lastCommandReceived = Some(CarChargerSignal.TurnOff))
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(
      initialState,
      calespiga.model.Event.System.StartupEvent,
      now
    )

    assertEquals(
      newState.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )
    assertEquals(newState.carCharger.lastChange, Some(now))

    val expectedActions = Set(
      Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
        config.resendInterval
      ),
      Action.SetUIItemValue(
        config.lastCommandItem,
        CarChargerSignal.userCommandToString(CarChargerSignal.TurnOff)
      )
    )

    assertEquals(actions, expectedActions)
  }

  test("StartupEvent with no previous command sends Off and UI shows off") {
    val initialState = stateWithCarCharger()
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(
      initialState,
      calespiga.model.Event.System.StartupEvent,
      now
    )

    assertEquals(
      newState.carCharger.lastCommandSent,
      Some(CarChargerSignal.Off)
    )
    assertEquals(newState.carCharger.lastChange, Some(now))

    val expectedActions = Set(
      Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
      Action.Periodic(
        config.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action.SendMqttStringMessage(config.mqttTopicForCommand, "off"),
        config.resendInterval
      ),
      Action.SetUIItemValue(
        config.lastCommandItem,
        CarChargerSignal.userCommandToString(CarChargerSignal.TurnOff)
      )
    )

    assertEquals(actions, expectedActions)
  }

}
