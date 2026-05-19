package calespiga.processor.carCharger

import munit.FunSuite
import calespiga.model.{State, Action, CarChargerSignal}
import calespiga.model.Event.CarCharger.*
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions

class CarChargerPowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2024-01-15T10:00:00Z")

  private val config = ProcessorConfigHelper.carCharger

  private def stateWithCarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      lastCommandSent: Option[CarChargerSignal.ControllerState] = None,
      lastCommandReceived: Option[CarChargerSignal.UserCommand] = None,
      lastChange: Option[Instant] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus = switchStatus,
          lastCommandSent = lastCommandSent,
          lastCommandReceived = lastCommandReceived,
          lastChange = lastChange,
          currentPowerWatts = None,
          lastEnergyUpdate = None,
          lastAccumulatedEnergyWh = None,
          accumulatedAtDayStartWh = None,
          online = None,
          chargingStatus = None
        )
      )

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
    "CarChargerPowerCommandChanged SetAutomatic sends Off to microcontroller"
  ) {
    val initialState = stateWithCarCharger()
    val event = CarChargerPowerCommandChanged(CarChargerSignal.SetAutomatic)
    val processor = CarChargerPowerProcessor(config)
    val (newState, actions) = processor.process(initialState, event, now)

    assertEquals(
      newState.carCharger.lastCommandReceived,
      Some(CarChargerSignal.SetAutomatic)
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
