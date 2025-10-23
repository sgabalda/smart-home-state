package calespiga.processor.utils

import munit.FunSuite
import calespiga.model.{RemoteState, Action}
import scala.concurrent.duration._

class RemoteStateActionManagerSuite extends FunSuite {

  // Dummy state type for testing
  enum TestState {
    case Off, On
  }
  import TestState.*

  val id = "heater"
  val resendInterval = 1.second
  val timeoutInterval = 5.seconds
  val mqttTopic = "heater/command"
  val uiItem = "HeaterSyncStatus"

  val manager = RemoteStateActionManager[TestState](
    id,
    resendInterval,
    timeoutInterval,
    mqttTopic,
    uiItem
  )

  val baseState = RemoteState[TestState](
    confirmed = Off,
    latestCommand = Off,
    currentInconsistencyStart = None
  )

  test("turnRemote: both command and state not in sync") {
    val (actions, newState) = manager.turnRemote(On, baseState)
    assertEquals(actions.size, 4)
    assert(
      actions.contains(
        Action
          .SetOpenHabItemValue(uiItem, RemoteStateActionManager.SYNCHRONIZED)
      ),
      "Should set sync status to SYNCHRONIZED"
    )
    assert(
      actions.contains(Action.SendMqttStringMessage(mqttTopic, "On")),
      "Should send MQTT command 'On'"
    )
    assert(
      actions.contains(
        Action.Periodic(
          id + RemoteStateActionManager.COMMAND_ACTION_SUFFIX,
          Action.SendMqttStringMessage(mqttTopic, "On"),
          resendInterval
        )
      ),
      "Should schedule periodic resend for 'On'"
    )
    assert(
      actions.contains(
        Action.Delayed(
          id + RemoteStateActionManager.INCONSISTENCY_TIMEOUT_ACTION_SUFFIX,
          Action.SetOpenHabItemValue(
            uiItem,
            RemoteStateActionManager.NOT_SYNCHRONIZED
          ),
          timeoutInterval
        )
      ),
      "Should schedule delayed inconsistency to NOT_SYNCHRONIZED"
    )
    assertEquals(newState.latestCommand, On)
    assertEquals(newState.confirmed, baseState.confirmed)
  }

  test("turnRemote: command not in sync, state in sync") {
    val state = baseState.copy(confirmed = On)
    val (actions, newState) = manager.turnRemote(On, state)
    assertEquals(actions.size, 4)
    assert(
      actions.contains(
        Action
          .SetOpenHabItemValue(uiItem, RemoteStateActionManager.SYNCHRONIZED)
      ),
      "Should set sync status to SYNCHRONIZED"
    )
    assert(
      actions.contains(Action.SendMqttStringMessage(mqttTopic, "On")),
      "Should send MQTT command 'On'"
    )
    assert(
      actions.contains(
        Action.Periodic(
          id + RemoteStateActionManager.COMMAND_ACTION_SUFFIX,
          Action.SendMqttStringMessage(mqttTopic, "On"),
          resendInterval
        )
      ),
      "Should schedule periodic resend for 'On'"
    )
    assert(
      actions.contains(
        Action.Cancel(
          id + RemoteStateActionManager.INCONSISTENCY_TIMEOUT_ACTION_SUFFIX
        )
      ),
      "Should cancel delayed inconsistency"
    )
    assertEquals(newState.latestCommand, On)
    assertEquals(newState.confirmed, state.confirmed)
  }

  test("turnRemote: command in sync, state not in sync") {
    val state = baseState.copy(latestCommand = On)
    val (actions, newState) = manager.turnRemote(On, state)
    assert(actions.isEmpty, "No actions should be produced")
    assertEquals(newState, state)
  }

  test("turnRemote: command and state in sync") {
    val state = baseState.copy(confirmed = On, latestCommand = On)
    val (actions, newState) = manager.turnRemote(On, state)
    assert(actions.isEmpty, "No actions should be produced")
    assertEquals(newState, state)
  }
}
