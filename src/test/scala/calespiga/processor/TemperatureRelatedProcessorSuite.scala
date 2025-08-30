package calespiga.processor

import calespiga.model.{Action, Event, Fixture, State, RemoteState}
import calespiga.model.Event.Temperature.*
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*
import calespiga.model.Switch
import java.time.Instant
import calespiga.model.RemoteSwitch
import calespiga.processor.RemoteStateActionProducer.*

class TemperatureRelatedProcessorSuite extends CatsEffectSuite {

  val now = Instant.now
  val dummyGoalTemp = 21.0

  // Default configuration for testing
  private val defaultConfig: calespiga.config.TemperatureRelatedConfig = {
    import scala.concurrent.duration.DurationInt
    calespiga.config.TemperatureRelatedConfig(
      resendInterval = 15.seconds,
      timeoutInterval = 1.minute,
      batteryTemperatureItem = "BateriesTemperaturaSHS",
      batteryClosetTemperatureItem = "BateriesTemperaturaAdosadaSHS",
      electronicsTemperatureItem = "ElectronicaTemperaturaSHS",
      externalTemperatureItem = "ExteriorArmarisTemperaturaSHS",
      batteryFanStatusItem = "VentiladorBateriesStatusSHS",
      batteryFanCommandItem = "VentiladorBateriesSetSHS",
      electronicsFanStatusItem = "VentiladorElectronicaStatusSHS",
      electronicsFanCommandItem = "VentiladorElectronicaSetSHS",
      fansInconsistencyItem = "VentiladorsInconsistencySHS",
      batteryFanMqttTopic = "fan/batteries/set",
      electronicsFanMqttTopic = "fan/electronics/set",
      batteryFanId = "ventilador-bateries",
      electronicsFanId = "ventilador-electronica"
    )
  }

  // Stub action producers that return predictable, simple actions for testing isolation
  private val batteryFanActionProducerStub: RemoteSwitchActionProducer =
    new RemoteStateActionProducer[Switch.Status] {
      def produceActionsForConfirmed(
          remoteState: RemoteState[Switch.Status],
          timestamp: Instant
      ): Set[Action] =
        Set(
          Action.SetOpenHabItemValue(
            "battery-fan-confirmed",
            remoteState.confirmed.toStatusString
          )
        )

      def produceActionsForCommand(
          remoteState: RemoteState[Switch.Status],
          timestamp: Instant
      ): Set[Action] =
        Set(
          Action.SetOpenHabItemValue(
            "battery-fan-command",
            remoteState.latestCommand.toCommandString
          )
        )
    }

  private val electronicsFanActionProducerStub: RemoteSwitchActionProducer =
    new RemoteStateActionProducer[Switch.Status] {
      def produceActionsForConfirmed(
          remoteState: RemoteState[Switch.Status],
          timestamp: Instant
      ): Set[Action] =
        Set(
          Action.SetOpenHabItemValue(
            "electronics-fan-confirmed",
            remoteState.confirmed.toStatusString
          )
        )

      def produceActionsForCommand(
          remoteState: RemoteState[Switch.Status],
          timestamp: Instant
      ): Set[Action] =
        Set(
          Action.SetOpenHabItemValue(
            "electronics-fan-command",
            remoteState.latestCommand.toCommandString
          )
        )
    }

  Fixture.allEvents
    .flatMap[(Event.EventData, State => State, Set[Action])](
      _.data match {
        case e @ BatteryTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.batteriesTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "BateriesTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ BatteryClosetTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.batteriesClosetTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "BateriesTemperaturaAdosadaSHS",
                  11d.toString
                )
              )
            )
          )

        case e @ ElectronicsTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.electronicsTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "ElectronicaTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ ExternalTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.externalTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "ExteriorArmarisTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ GoalTemperatureChanged(_) =>
          List(
            (
              e.modify(_.celsius).setTo(dummyGoalTemp),
              _.modify(_.temperatures.goalTemperature).setTo(dummyGoalTemp),
              Set.empty[Action]
            )
          )
        case e @ Fans.BatteryFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.On, Switch.Off, Some(now))),
              Set(
                Action.SetOpenHabItemValue("battery-fan-confirmed", "on")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("battery-fan-confirmed", "off")
              )
            )
          )
        case e @ Fans.ElectronicsFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.On, Switch.Off, Some(now))),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-confirmed", "on")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-confirmed", "off")
              )
            )
          )
        case e @ Fans.BatteryFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.On, Some(now))),
              Set(
                Action.SetOpenHabItemValue("battery-fan-command", "start")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("battery-fan-command", "stop")
              )
            )
          )
        case e @ Fans.ElectronicsFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.On, Some(now))),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-command", "start")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-command", "stop")
              )
            )
          )
        case Event.Temperature.Fans.FanManagementChanged(value) =>
          List(
            (
              Event.Temperature.Fans.FanManagementChanged(value),
              _.modify(_.fans.fanManagementAutomatic).setTo(value),
              Set.empty[Action]
            )
          )
        case Event.System.StartupEvent =>
          List(
            (
              Event.System.StartupEvent,
              s => s, // State may be updated if inconsistency timestamps exist
              Set.empty[Action]
              // Actions depend on the actual state, which varies in tests
            )
          )
        // case e => List((e, s => s, Set.empty)) for when more events are to be defined
      }
    )
    .foreach { (event, newState, expectedActions) =>
      test(
        s"TemperatureRelatedProcessor processes $event"
      ) {
        val sut = TemperatureRelatedProcessor(
          defaultConfig,
          batteryFanActionProducer = batteryFanActionProducerStub,
          electronicsFanActionProducer = electronicsFanActionProducerStub
        )
        val (state, actions) = sut.process(Fixture.state, event, now)
        assertEquals(state, newState(Fixture.state))
        assertEquals(actions, expectedActions)
      }
    }

  // Test automatic fan management behavior
  val automaticModeState =
    Fixture.state.modify(_.fans.fanManagementAutomatic).setTo(Switch.On)

  test(
    "TemperatureRelatedProcessor with automatic management - BatteryTemperatureMeasured does not trigger fan control"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // BatteryTemperatureMeasured only updates batteriesTemperature, not batteriesClosetTemperature
    // So it should NOT trigger automatic fan control (which uses batteriesClosetTemperature)
    val testState = automaticModeState
      .modify(_.temperatures.externalTemperature)
      .setTo(15.0)
      .modify(_.temperatures.batteriesTemperature)
      .setTo(25.0) // Change from default 30.0

    val event = BatteryTemperatureMeasured(
      35.0
    ) // Different from 25.0 to trigger the condition
    val (state, actions) = sut.process(testState, event, now)

    // Should update temperature but NOT trigger fan control
    assertEquals(state.temperatures.batteriesTemperature, 35.0)
    // Check that the temperature update action is there
    val expectedTempAction =
      Action.SetOpenHabItemValue("BateriesTemperaturaSHS", "35.0")
    assert(
      actions.contains(expectedTempAction),
      s"Expected $expectedTempAction in $actions"
    )
    assert(!actions.exists(_.toString.contains("battery-fan-command")))
  }

  test(
    "TemperatureRelatedProcessor with automatic management - BatteryClosetTemperatureMeasured triggers fan control"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up scenario: high closet temperature (30°C) vs low external (15°C) - should turn fan ON
    val testState = automaticModeState
      .modify(_.temperatures.externalTemperature)
      .setTo(15.0)

    val event = BatteryClosetTemperatureMeasured(30.0)
    val (state, actions) = sut.process(testState, event, now)

    // Should update temperature and trigger fan control
    assertEquals(state.temperatures.batteriesClosetTemperature, 30.0)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("BateriesTemperaturaAdosadaSHS", "30.0")
      )
    )
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("battery-fan-command", "start")
      )
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - ElectronicsTemperatureMeasured triggers fan control"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up scenario: high electronics temperature (30°C) vs low external (15°C) - should turn fan ON
    val testState = automaticModeState
      .modify(_.temperatures.externalTemperature)
      .setTo(15.0)

    val event = ElectronicsTemperatureMeasured(30.0)
    val (state, actions) = sut.process(testState, event, now)

    // Should update temperature and trigger fan control
    assertEquals(state.temperatures.electronicsTemperature, 30.0)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("ElectronicaTemperaturaSHS", "30.0")
      )
    )
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("electronics-fan-command", "start")
      )
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - ExternalTemperatureMeasured triggers both fans control"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up scenario: high internal temperatures vs new low external temperature
    // Goal is 20°C, so fans should turn on when external temp is closer to 20 than internal temps
    val testState = automaticModeState
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(30.0) // 10°C away from goal
      .modify(_.temperatures.electronicsTemperature)
      .setTo(28.0) // 8°C away from goal

    val event = ExternalTemperatureMeasured(
      19.0
    ) // 1°C away from goal - closer than internal temps
    val (state, actions) = sut.process(testState, event, now)

    // Should update external temperature and trigger both fans
    assertEquals(state.temperatures.externalTemperature, 19.0)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("ExteriorArmarisTemperaturaSHS", "19.0")
      )
    )
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("battery-fan-command", "start")
      )
    )
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("electronics-fan-command", "start")
      )
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - no fan action when temperatures don't warrant it"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up scenario: internal and external temperatures are similar - should NOT turn fans ON
    val testState = automaticModeState
      .modify(_.temperatures.externalTemperature)
      .setTo(22.0)

    val event =
      BatteryTemperatureMeasured(21.0) // only 1°C difference, not worth cooling
    val (state, actions) = sut.process(testState, event, now)

    // Should update temperature but NOT trigger fan control
    assertEquals(state.temperatures.batteriesTemperature, 21.0)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("BateriesTemperaturaSHS", "21.0")
      )
    )
    assert(!actions.exists(_.toString.contains("battery-fan-command")))
  }

  test(
    "TemperatureRelatedProcessor with automatic management - BatteryFanSwitchManualChanged is ignored when command matches state"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // automaticModeState has fanBatteries.latestCommand = Switch.Off (default)
    val event =
      Fans.BatteryFanSwitchManualChanged(Switch.Off) // Same as current state
    val (state, actions) = sut.process(automaticModeState, event, now)

    // In automatic mode, manual changes are ignored - fan state should not change
    assertEquals(state.fans.fanBatteries.latestCommand, Switch.Off) // unchanged
    assertEquals(
      state.fans.fanManagementAutomatic,
      Switch.On
    ) // still automatic
    // No action needed since command matches current state
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "TemperatureRelatedProcessor with automatic management - BatteryFanSwitchManualChanged corrects OH item when command differs"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up state where fan is OFF but user tries to turn it ON
    val event = Fans.BatteryFanSwitchManualChanged(
      Switch.On
    ) // Different from current state (Off)
    val (state, actions) = sut.process(automaticModeState, event, now)

    // In automatic mode, manual changes are ignored - fan state should not change
    assertEquals(state.fans.fanBatteries.latestCommand, Switch.Off) // unchanged
    assertEquals(
      state.fans.fanManagementAutomatic,
      Switch.On
    ) // still automatic
    // Should correct the OH item to current state value
    assertEquals(
      actions,
      Set(Action.SetOpenHabItemValue("VentiladorBateriesSetSHS", "off"))
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - ElectronicsFanSwitchManualChanged is ignored when command matches state"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // automaticModeState has fanElectronics.latestCommand = Switch.Off (default)
    val event = Fans.ElectronicsFanSwitchManualChanged(
      Switch.Off
    ) // Same as current state
    val (state, actions) = sut.process(automaticModeState, event, now)

    // In automatic mode, manual changes are ignored - fan state should not change
    assertEquals(
      state.fans.fanElectronics.latestCommand,
      Switch.Off
    ) // unchanged
    assertEquals(
      state.fans.fanManagementAutomatic,
      Switch.On
    ) // still automatic
    // No action needed since command matches current state
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "TemperatureRelatedProcessor with automatic management - ElectronicsFanSwitchManualChanged corrects OH item when command differs"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up state where fan is OFF but user tries to turn it ON
    val event = Fans.ElectronicsFanSwitchManualChanged(
      Switch.On
    ) // Different from current state (Off)
    val (state, actions) = sut.process(automaticModeState, event, now)

    // In automatic mode, manual changes are ignored - fan state should not change
    assertEquals(
      state.fans.fanElectronics.latestCommand,
      Switch.Off
    ) // unchanged
    assertEquals(
      state.fans.fanManagementAutomatic,
      Switch.On
    ) // still automatic
    // Should correct the OH item to current state value
    assertEquals(
      actions,
      Set(Action.SetOpenHabItemValue("VentiladorElectronicaSetSHS", "off"))
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - FanManagementChanged from On to Off"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    val event = Event.Temperature.Fans.FanManagementChanged(Switch.Off)
    val (state, actions) = sut.process(automaticModeState, event, now)

    // Should disable automatic management without updating any OpenHAB items
    assertEquals(state.fans.fanManagementAutomatic, Switch.Off)
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "TemperatureRelatedProcessor with automatic management - bug fix: external=5°C, internal=30°C should turn fan ON"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Test case from the bug report: external=5°C, internal=30°C, goal=20°C
    // Should turn fan ON because external air (5°C) is cooler than internal (30°C)
    // even though external is farther from goal (|5-20|=15 > |30-20|=10)
    val testState = automaticModeState
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(30.0)

    val event = ExternalTemperatureMeasured(5.0)
    val (state, actions) = sut.process(testState, event, now)

    // Should update external temperature and turn fan ON to bring cool air inside
    assertEquals(state.temperatures.externalTemperature, 5.0)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("ExteriorArmarisTemperaturaSHS", "5.0")
      )
    )
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("battery-fan-command", "start")
      )
    )
  }

  test(
    "TemperatureRelatedProcessor with automatic management - GoalTemperatureChanged triggers fan re-evaluation"
  ) {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Set up scenario: internal=25°C, external=22°C, current goal=20°C
    // With goal=20°C: internal (25°C) too hot, external (22°C) cooler → fan should be ON
    // With new goal=30°C: internal (25°C) too cool, external (22°C) cooler → fan should be OFF
    val testState = automaticModeState
      .modify(_.temperatures.batteriesClosetTemperature)
      .setTo(25.0)
      .modify(_.temperatures.externalTemperature)
      .setTo(22.0)
      .modify(_.temperatures.goalTemperature)
      .setTo(20.0) // current goal
      .modify(_.fans.fanBatteries.latestCommand)
      .setTo(Switch.On) // fan is initially ON due to current temp conditions

    val event = GoalTemperatureChanged(30.0) // new goal
    val (state, actions) = sut.process(testState, event, now)

    // Should update goal temperature and re-evaluate fans
    assertEquals(state.temperatures.goalTemperature, 30.0)
    // With new goal=30°C, internal temp (25°C) is now too cool, and external (22°C) is cooler
    // so fan should turn OFF (external air wouldn't help warm up)
    assert(
      actions.contains(
        Action.SetOpenHabItemValue("battery-fan-command", "stop")
      )
    )
  }

  test("TemperatureRelatedProcessor handles StartupEvent correctly") {
    val sut = TemperatureRelatedProcessor(
      defaultConfig,
      batteryFanActionProducer = batteryFanActionProducerStub,
      electronicsFanActionProducer = electronicsFanActionProducerStub
    )

    // Create test state with inconsistency and different command vs confirmed state
    val testState = Fixture.state
      .modify(_.fans.fanBatteries)
      .setTo(RemoteSwitch(Switch.Off, Switch.On, Some(now.minusSeconds(30))))
      .modify(_.fans.fanElectronics)
      .setTo(RemoteSwitch(Switch.On, Switch.Off, Some(now.minusSeconds(10))))

    val (state, actions) =
      sut.process(testState, Event.System.StartupEvent, now)

    // State should be updated with reset inconsistency timestamps
    assertEquals(state.fans.fanBatteries.currentInconsistencyStart, Some(now))
    assertEquals(state.fans.fanElectronics.currentInconsistencyStart, Some(now))

    // Command and confirmed states should remain unchanged
    assertEquals(state.fans.fanBatteries.latestCommand, Switch.On)
    assertEquals(state.fans.fanBatteries.confirmed, Switch.Off)
    assertEquals(state.fans.fanElectronics.latestCommand, Switch.Off)
    assertEquals(state.fans.fanElectronics.confirmed, Switch.On)

    // Should produce actions for both fans due to inconsistencies and command states
    assert(actions.nonEmpty)

    // Should have exactly 4 actions: 2 for each fan (confirmed + command)
    assertEquals(actions.size, 4)

    // Should include actions for both fans
    val batteryFanActions = actions.filter(_.toString.contains("battery-fan"))
    val electronicsFanActions =
      actions.filter(_.toString.contains("electronics-fan"))

    assertEquals(batteryFanActions.size, 2)
    assertEquals(electronicsFanActions.size, 2)
  }

}
