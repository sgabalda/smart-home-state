package calespiga.processor

import munit.FunSuite
import calespiga.model.{State, Action, Switch}
import calespiga.model.RemoteHeaterPowerState
import calespiga.model.RemoteHeaterPowerState.RemoteHeaterPowerStatus
import calespiga.model.Event.Heater.*
import java.time.Instant
import calespiga.processor.utils.RemoteStateActionManager
import com.softwaremill.quicklens.*

class HeaterProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val initialRemoteState = RemoteHeaterPowerState.apply(
    RemoteHeaterPowerStatus.Off,
    RemoteHeaterPowerStatus.Off,
    None
  )
  private val resultRemoteState = RemoteHeaterPowerState.apply(
    RemoteHeaterPowerStatus.Off,
    RemoteHeaterPowerStatus.Off,
    None
  )
  private val initialStateManual = State(
    heater = State.Heater(
      status = initialRemoteState,
      isHot = Switch.Off,
      lastTimeHot = None,
      energyToday = 0.0f,
      heaterManagementAutomatic = Switch.Off,
      lastCommandReceived = None
    )
  )

  // Stub manager that records calls and returns fixed actions/state
  class StubManager extends RemoteStateActionManager[RemoteHeaterPowerStatus] {
    var calledWith: Option[
      (RemoteHeaterPowerStatus, RemoteHeaterPowerState.RemoteHeaterPowerState)
    ] = None
    var actionsToReturn: Set[Action] = Set(
      Action.SendMqttStringMessage("topic", "payload")
    )
    var stateToReturn: RemoteHeaterPowerState.RemoteHeaterPowerState =
      resultRemoteState
    override def turnRemote(
        commandToSet: RemoteHeaterPowerStatus,
        currentState: RemoteHeaterPowerState.RemoteHeaterPowerState
    ) = {
      calledWith = Some((commandToSet, currentState))
      (actionsToReturn, stateToReturn)
    }
  }

  test(
    "HeaterPowerCommandChanged in manual mode applies command and updates lastCommandReceived"
  ) {
    val stub = new StubManager
    val processor = HeaterProcessor(stub)
    val event = HeaterPowerCommandChanged(RemoteHeaterPowerStatus.Power500)
    val (newState, actions) = processor.process(initialStateManual, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Power500, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(
      newState.heater.lastCommandReceived,
      Some(RemoteHeaterPowerStatus.Power500)
    )
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  test(
    "HeaterIsHotReported(Switch.Off) in manual mode propagates last user command"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Power500)
    val stateWithLastCmd = initialStateManual
      .modify(_.heater.lastCommandReceived)
      .setTo(Some(RemoteHeaterPowerStatus.Power500))
    val processor = HeaterProcessor(stub)
    val event = HeaterIsHotReported(Switch.Off)
    val (newState, actions) = processor.process(stateWithLastCmd, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Power500, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.isHot, Switch.Off)
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  test(
    "HeaterIsHotReported(Switch.On) in manual mode turns heater off and marks as hot"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Off)
    val processor = HeaterProcessor(stub)
    val event = HeaterIsHotReported(Switch.On)
    val (newState, actions) = processor.process(initialStateManual, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Off, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.isHot, Switch.On)
    assertEquals(newState.heater.lastTimeHot, Some(now))
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  test(
    "HeaterManagementAutomaticChanged(Switch.Off) applies last command received"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Power1000)
    val stateWithLastCmd = initialStateManual
      .modify(_.heater.lastCommandReceived)
      .setTo(Some(RemoteHeaterPowerStatus.Power1000))
    val processor = HeaterProcessor(stub)
    val event = HeaterManagementAutomaticChanged(Switch.Off)
    val (newState, actions) = processor.process(stateWithLastCmd, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Power1000, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.heaterManagementAutomatic, Switch.Off)
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  private val initialStateAutomatic = State(
    heater = State.Heater(
      status = initialRemoteState,
      isHot = Switch.Off,
      lastTimeHot = None,
      energyToday = 0.0f,
      heaterManagementAutomatic = Switch.On,
      lastCommandReceived = None
    )
  )

  test(
    "HeaterPowerCommandChanged in automatic mode stores command but does not apply it"
  ) {
    val stub = new StubManager
    val processor = HeaterProcessor(stub)
    val event = HeaterPowerCommandChanged(RemoteHeaterPowerStatus.Power500)
    val (newState, actions) =
      processor.process(initialStateAutomatic, event, now)
    assertEquals(
      stub.calledWith,
      None,
      "RemoteStateActionManager should not be called"
    )
    assertEquals(actions, Set.empty, "No actions should be produced")
    assertEquals(
      newState.heater.lastCommandReceived,
      Some(RemoteHeaterPowerStatus.Power500)
    )
    assertEquals(newState.heater.status, initialRemoteState)
  }

  test(
    "HeaterIsHotReported(Switch.Off) in automatic mode turns heater off"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Off)
    val processor = HeaterProcessor(stub)
    val event = HeaterIsHotReported(Switch.Off)
    val (newState, actions) =
      processor.process(initialStateAutomatic, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Off, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.isHot, Switch.Off)
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  test(
    "HeaterIsHotReported(Switch.On) in automatic mode turns heater off and marks as hot"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Off)
    val processor = HeaterProcessor(stub)
    val event = HeaterIsHotReported(Switch.On)
    val (newState, actions) =
      processor.process(initialStateAutomatic, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Off, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.isHot, Switch.On)
    assertEquals(newState.heater.lastTimeHot, Some(now))
    assertEquals(newState.heater.status, stub.stateToReturn)
  }

  test(
    "HeaterManagementAutomaticChanged(Switch.On) turns heater off and sets automatic mode"
  ) {
    val stub = new StubManager
    stub.stateToReturn =
      initialRemoteState.copy(confirmed = RemoteHeaterPowerStatus.Off)
    val processor = HeaterProcessor(stub)
    val event = HeaterManagementAutomaticChanged(Switch.On)
    val (newState, actions) = processor.process(initialStateManual, event, now)
    assertEquals(
      stub.calledWith,
      Some((RemoteHeaterPowerStatus.Off, initialRemoteState))
    )
    assertEquals(actions, stub.actionsToReturn)
    assertEquals(newState.heater.heaterManagementAutomatic, Switch.On)
    assertEquals(newState.heater.status, stub.stateToReturn)
  }
}
