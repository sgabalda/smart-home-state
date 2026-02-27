package calespiga.processor.heater

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.model.Event.Heater.*
import calespiga.model.HeaterSignal
import java.time.Instant
import com.softwaremill.quicklens.*
import java.time.ZoneId
import calespiga.model.State.Heater
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.EnergyCalculatorStub
import calespiga.processor.utils.ProcessorFormatter
import scala.collection.mutable
import calespiga.processor.utils.CommandActions

class HeaterPowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")

  private val zone: ZoneId = ZoneId.systemDefault()

  // Use HeaterConfig from helper
  private val dummyConfig = ProcessorConfigHelper.heaterConfig

  private def stateWithHeater(
      status: Option[HeaterSignal.ControllerState] = Some(HeaterSignal.Off),
      lastCommandSent: Option[HeaterSignal.ControllerState] = None,
      lastCommandReceived: Option[HeaterSignal.UserCommand] = None,
      lastChange: Option[Instant] = None,
      isHot: HeaterSignal.HeaterTermostateState = HeaterSignal.Cold,
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
    val calculatedEnergy = 1500f
    val oneHourAgo = now.minusSeconds(secondsAgo)
    val initialState = stateWithHeater(
      status = Some(HeaterSignal.Power500),
      lastChange = Some(oneHourAgo),
      energyToday = initialEnergy
    )

    // Track calls to the energy calculator
    val calculatorCalls = mutable.ListBuffer
      .empty[(Option[Instant], Instant, Int, Float, ZoneId)]
    val energyCalculator = EnergyCalculatorStub(
      calculateEnergyTodayStub =
        (lastChange, timestamp, power, energy, zone) => {
          calculatorCalls.addOne((lastChange, timestamp, power, energy, zone))
          calculatedEnergy
        }
    )

    val event = HeaterPowerStatusReported(HeaterSignal.Power1000)
    val processor = HeaterPowerProcessor(dummyConfig, zone, energyCalculator)
    val (newState, actions) = processor.process(initialState, event, now)

    // Verify EnergyCalculator was called with correct parameters
    assertEquals(calculatorCalls.size, 1)
    val (callLastChange, callTimestamp, callPower, callEnergy, callZone) =
      calculatorCalls.head
    assertEquals(callLastChange, Some(oneHourAgo))
    assertEquals(callTimestamp, now)
    assertEquals(callPower, HeaterSignal.Power500.power)
    assertEquals(callEnergy, initialEnergy)
    assertEquals(callZone, zone)

    // Verify state was updated with calculator result
    assertEquals(newState.heater.status, Some(HeaterSignal.Power1000))
    assertEquals(newState.heater.lastChange, Some(now))
    assertEqualsDouble(newState.heater.energyToday, calculatedEnergy, 0.1f)

    val expectedActions: Set[Action] = Set(
      Action.SetUIItemValue(
        dummyConfig.energyTodayItem,
        calculatedEnergy.toInt.toString
      ),
      Action.SetUIItemValue(
        dummyConfig.statusItem,
        HeaterSignal.Power1000.power.toString
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("HeaterPowerStatusReported resets energy if new day") {
    val initialEnergy = 10000f
    val calculatedEnergy = 500f
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

    // Track calls to the energy calculator
    val calculatorCalls2 = mutable.ListBuffer
      .empty[(Option[Instant], Instant, Int, Float, ZoneId)]
    val energyCalculator2 = EnergyCalculatorStub(
      calculateEnergyTodayStub =
        (lastChange, timestamp, power, energy, zone) => {
          calculatorCalls2.addOne((lastChange, timestamp, power, energy, zone))
          calculatedEnergy
        }
    )

    val event = HeaterPowerStatusReported(HeaterSignal.Power1000)
    val processor = HeaterPowerProcessor(dummyConfig, zone, energyCalculator2)
    val (newState2, actions2) =
      processor.process(initialStateNewDay, event, today)

    // Verify EnergyCalculator was called with correct parameters
    assertEquals(calculatorCalls2.size, 1)
    val (callLastChange2, callTimestamp2, callPower2, callEnergy2, callZone2) =
      calculatorCalls2.head
    assertEquals(callLastChange2, Some(yesterday))
    assertEquals(callTimestamp2, today)
    assertEquals(callPower2, HeaterSignal.Power500.power)
    assertEquals(callEnergy2, initialEnergy)
    assertEquals(callZone2, zone)

    // Verify state was updated with calculator result
    assertEqualsDouble(
      newState2.heater.energyToday,
      calculatedEnergy,
      0.1f,
      "energyToday should be set to calculator result"
    )

    val expectedActions: Set[Action] = Set(
      Action.SetUIItemValue(
        dummyConfig.energyTodayItem,
        calculatedEnergy.toInt.toString
      ),
      Action.SetUIItemValue(
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
        dummyConfig.id + CommandActions.COMMAND_ACTION_SUFFIX,
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
    val initialState = stateWithHeater(isHot = HeaterSignal.Cold)
    val event = HeaterIsHotReported(HeaterSignal.Hot)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(newState.heater.isHot, HeaterSignal.Hot)
    assertEquals(newState.heater.lastTimeHot, Some(now))
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Off))
    val expectedActions = Set(
      Action.SendMqttStringMessage("dummy/topic", "0"),
      Action.Periodic(
        "heater-processor-command",
        Action.SendMqttStringMessage("dummy/topic", "0"),
        scala.concurrent.duration.DurationInt(20).seconds
      ),
      Action.SetUIItemValue(
        dummyConfig.lastTimeHotItem,
        ProcessorFormatter.format(now, zone)
      ),
      Action.SetUIItemValue(
        dummyConfig.isHotItem,
        HeaterSignal.Hot.toString
      )
    )
    assertEquals(actions, expectedActions)
  }

  test(
    "HeaterIsHotReported(Off) sets isHot, sends last user command and no UI update"
  ) {
    val initialState = stateWithHeater(
      isHot = HeaterSignal.Hot,
      lastCommandReceived = Some(HeaterSignal.SetPower500)
    )
    val event = HeaterIsHotReported(HeaterSignal.Cold)
    val processor = HeaterPowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(newState.heater.isHot, HeaterSignal.Cold)
    assertEquals(newState.heater.lastCommandSent, Some(HeaterSignal.Power500))
    val expectedActions = Set(
      Action.SendMqttStringMessage("dummy/topic", "500"),
      Action.Periodic(
        "heater-processor-command",
        Action.SendMqttStringMessage("dummy/topic", "500"),
        scala.concurrent.duration.DurationInt(20).seconds
      ),
      Action.SetUIItemValue(
        dummyConfig.lastTimeHotItem,
        ProcessorFormatter.format(now, zone)
      ),
      Action.SetUIItemValue(
        dummyConfig.isHotItem,
        HeaterSignal.Cold.toString
      )
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
      ),
      Action.SetUIItemValue(
        dummyConfig.lastCommandItem,
        HeaterSignal.userCommandToString(HeaterSignal.SetPower2000)
      )
    )
    assertEquals(actions, expectedActions)
  }
}
