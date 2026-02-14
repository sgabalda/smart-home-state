package calespiga.processor.infraredStove

import munit.FunSuite
import calespiga.model.{State, Action}
import calespiga.model.Event.InfraredStove.*
import calespiga.model.InfraredStoveSignal
import java.time.Instant
import com.softwaremill.quicklens.*
import java.time.ZoneId
import calespiga.model.State.InfraredStove
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.EnergyCalculatorStub
import scala.collection.mutable
import calespiga.processor.utils.CommandActions

class InfraredStovePowerProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")

  private val zone: ZoneId = ZoneId.systemDefault()

  // Use InfraredStoveConfig from helper
  private val dummyConfig = ProcessorConfigHelper.infraredStoveConfig

  private def stateWithInfraredStove(
      status: Option[InfraredStoveSignal.ControllerState] = Some(
        InfraredStoveSignal.Off
      ),
      lastCommandSent: Option[InfraredStoveSignal.ControllerState] = None,
      lastCommandReceived: Option[InfraredStoveSignal.UserCommand] = None,
      lastChange: Option[Instant] = None,
      energyToday: Float = 0.0f
  ): State =
    State()
      .modify(_.infraredStove)
      .setTo(
        State.InfraredStove(
          status,
          lastCommandSent,
          lastCommandReceived,
          lastChange,
          None, // manualMaxTimeMinutes
          None, // lastSetManual
          None, // lastTimeConnected
          energyToday,
          None, // lastOnline
          None // lastSyncing
        )
      )

  test("InfraredStovePowerStatusReported accumulates energy if same day") {
    val secondsAgo = 3600
    val initialEnergy = 1000f
    val calculatedEnergy = 1600f
    val oneHourAgo = now.minusSeconds(secondsAgo)
    val initialState = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600),
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

    val event = InfraredStovePowerStatusReported(InfraredStoveSignal.Power1200)
    val processor =
      InfraredStovePowerProcessor(dummyConfig, zone, energyCalculator)
    val (newState, actions) = processor.process(initialState, event, now)

    // Verify EnergyCalculator was called with correct parameters
    assertEquals(calculatorCalls.size, 1)
    val (callLastChange, callTimestamp, callPower, callEnergy, callZone) =
      calculatorCalls.head
    assertEquals(callLastChange, Some(oneHourAgo))
    assertEquals(callTimestamp, now)
    assertEquals(callPower, InfraredStoveSignal.Power600.power)
    assertEquals(callEnergy, initialEnergy)
    assertEquals(callZone, zone)

    // Verify state was updated with calculator result
    assertEquals(
      newState.infraredStove.status,
      Some(InfraredStoveSignal.Power1200)
    )
    assertEquals(newState.infraredStove.lastChange, Some(now))
    assertEqualsDouble(
      newState.infraredStove.energyToday,
      calculatedEnergy,
      0.1f
    )

    val expectedActions: Set[Action] = Set(
      Action.SetUIItemValue(
        dummyConfig.energyTodayItem,
        calculatedEnergy.toInt.toString
      ),
      Action.SetUIItemValue(
        dummyConfig.statusItem,
        InfraredStoveSignal.Power1200.power.toString
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("InfraredStovePowerStatusReported resets energy if new day") {
    val initialEnergy = 10000f
    val calculatedEnergy = 600f
    val yesterday = now
      .atZone(zone)
      .toLocalDate
      .minusDays(1)
      .atTime(java.time.LocalTime.MAX)
      .atZone(zone)
      .toInstant
    val today = yesterday.plusSeconds(3600)
    val initialStateNewDay = stateWithInfraredStove(
      status = Some(InfraredStoveSignal.Power600),
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

    val event = InfraredStovePowerStatusReported(InfraredStoveSignal.Power1200)
    val processor =
      InfraredStovePowerProcessor(dummyConfig, zone, energyCalculator2)
    val (newState2, actions2) =
      processor.process(initialStateNewDay, event, today)

    // Verify EnergyCalculator was called with correct parameters
    assertEquals(calculatorCalls2.size, 1)
    val (callLastChange2, callTimestamp2, callPower2, callEnergy2, callZone2) =
      calculatorCalls2.head
    assertEquals(callLastChange2, Some(yesterday))
    assertEquals(callTimestamp2, today)
    assertEquals(callPower2, InfraredStoveSignal.Power600.power)
    assertEquals(callEnergy2, initialEnergy)
    assertEquals(callZone2, zone)

    // Verify state was updated with calculator result
    assertEqualsDouble(
      newState2.infraredStove.energyToday,
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
        InfraredStoveSignal.Power1200.power.toString
      )
    )
    assertEquals(actions2, expectedActions)
  }

  test(
    "InfraredStovePowerCommandChanged stores user and controller command, sends correct actions"
  ) {
    val initialState = stateWithInfraredStove()
    val event =
      InfraredStovePowerCommandChanged(InfraredStoveSignal.SetPower1200)
    val processor = InfraredStovePowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(initialState, event, now)
    assertEquals(
      newState.infraredStove.lastCommandReceived,
      Some(InfraredStoveSignal.SetPower1200)
    )
    assertEquals(
      newState.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power1200)
    )
    val expectedActions = Set(
      Action
        .SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "1200"),
      Action.Periodic(
        dummyConfig.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action
          .SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "1200"),
        dummyConfig.resendInterval
      )
    )
    assertEquals(actions, expectedActions)
  }

  test("StartupEvent sends last user command or Off if none") {
    val initialState =
      stateWithInfraredStove(
        lastCommandReceived = Some(InfraredStoveSignal.SetPower600)
      )
    val processor = InfraredStovePowerProcessor(dummyConfig, zone)
    val (newState, actions) = processor.process(
      initialState,
      calespiga.model.Event.System.StartupEvent,
      now
    )
    assertEquals(
      newState.infraredStove.lastCommandSent,
      Some(InfraredStoveSignal.Power600)
    )
    assertEquals(newState.infraredStove.lastChange, Some(now))
    val expectedActions = Set(
      Action.SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "600"),
      Action.Periodic(
        dummyConfig.id + CommandActions.COMMAND_ACTION_SUFFIX,
        Action
          .SendMqttStringMessage(dummyConfig.mqttTopicForCommand, "600"),
        dummyConfig.resendInterval
      ),
      Action.SetUIItemValue(
        dummyConfig.lastCommandItem,
        InfraredStoveSignal.userCommandToString(
          InfraredStoveSignal.SetPower600
        )
      )
    )
    assertEquals(actions, expectedActions)
  }
}
