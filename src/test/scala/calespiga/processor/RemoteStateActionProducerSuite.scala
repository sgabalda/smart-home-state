package calespiga.processor

import munit.FunSuite
import java.time.Instant
import scala.concurrent.duration._
import calespiga.model.{Action, RemoteState, Switch}

class RemoteStateActionProducerSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val confirmedStateUIItem = "switch-ui"
  private val mqttTopicForCommand = "command/switch/topic"
  private val inconsistencyUIItem = "switch-inconsistency"
  private val id = "test-switch"
  private val resendInterval = 30.seconds
  private val timeoutInterval = 2.minutes

  private val producer = RemoteStateActionProducer(
    confirmedStateUIItem = confirmedStateUIItem,
    mqttTopicForCommand = mqttTopicForCommand,
    inconsistencyUIItem = inconsistencyUIItem,
    id = id,
    resendInterval = resendInterval,
    timeoutInterval = timeoutInterval
  )

  test(
    "produceActionsForCommand - produces MQTT command with correct parameters"
  ) {
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, None)

    val actions = producer.produceActionsForCommand(remoteState, now)

    val mqttAction = actions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }
    assert(mqttAction.isDefined, "Should produce MQTT command action")
    assertEquals(mqttAction.get.topic, mqttTopicForCommand)
    assertEquals(mqttAction.get.message, "start") // Switch.On.toCommandString
  }

  test(
    "produceActionsForCommand - produces periodic resend with correct parameters"
  ) {
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, None)

    val actions = producer.produceActionsForCommand(remoteState, now)

    val periodicAction = actions.collectFirst { case a: Action.Periodic => a }
    assert(periodicAction.isDefined, "Should produce periodic resend action")
    assertEquals(periodicAction.get.id, s"$id-command")
    assertEquals(periodicAction.get.period, resendInterval)

    // The periodic action should wrap the same MQTT command
    periodicAction.get.action match {
      case Action.SendMqttStringMessage(topic, message) =>
        assertEquals(topic, mqttTopicForCommand)
        assertEquals(message, "start")
      case _ => fail("Periodic action should wrap MQTT command")
    }
  }

  test(
    "produceActionsForCommand - no inconsistency, sets online and cancels timeout"
  ) {
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, None)

    val actions = producer.produceActionsForCommand(remoteState, now)

    val setOnlineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Online")
          if item == inconsistencyUIItem =>
        item
    }
    assert(setOnlineAction.isDefined, "Should set inconsistency item to Online")

    val cancelAction = actions.collectFirst { case a: Action.Cancel => a }
    assert(cancelAction.isDefined, "Should cancel timeout")
    assertEquals(cancelAction.get.id, s"$id-timeout")
  }

  test(
    "produceActionsForCommand - recent inconsistency, sets online and schedules timeout"
  ) {
    val inconsistencyStart =
      now.minusSeconds(30) // 30 seconds ago, less than 2 minutes timeout
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, Some(inconsistencyStart))

    val actions = producer.produceActionsForCommand(remoteState, now)

    val setOnlineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Online")
          if item == inconsistencyUIItem =>
        item
    }
    assert(setOnlineAction.isDefined, "Should set inconsistency item to Online")

    val delayedAction = actions.collectFirst { case a: Action.Delayed => a }
    assert(delayedAction.isDefined, "Should schedule timeout")
    assertEquals(delayedAction.get.id, s"$id-timeout")

    // Check the delayed action sets offline
    delayedAction.get.action match {
      case Action.SetOpenHabItemValue(item, "Offline")
          if item == inconsistencyUIItem =>
        // Verify delay is approximately correct (timeout - elapsed time)
        val expectedDelay = timeoutInterval - 30.seconds
        assert(
          delayedAction.get.delay.toSeconds >= expectedDelay.toSeconds - 1,
          "Delay should be approximately correct (lower bound)"
        )
        assert(
          delayedAction.get.delay.toSeconds <= expectedDelay.toSeconds + 1,
          "Delay should be approximately correct (upper bound)"
        )
      case _ => fail("Delayed action should set inconsistency item to Offline")
    }
  }

  test(
    "produceActionsForCommand - old inconsistency past timeout, sets offline and cancels timeout"
  ) {
    val inconsistencyStart =
      now.minusSeconds(180) // 3 minutes ago, past 2 minutes timeout
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, Some(inconsistencyStart))

    val actions = producer.produceActionsForCommand(remoteState, now)

    val setOfflineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Offline")
          if item == inconsistencyUIItem =>
        item
    }
    assert(
      setOfflineAction.isDefined,
      "Should set inconsistency item to Offline"
    )

    val cancelAction = actions.collectFirst { case a: Action.Cancel => a }
    assert(cancelAction.isDefined, "Should cancel timeout")
    assertEquals(cancelAction.get.id, s"$id-timeout")
  }

  test(
    "produceActionsForConfirmed - produces UI update with correct parameters"
  ) {
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.On, Switch.Off, None)

    val actions = producer.produceActionsForConfirmed(remoteState, now)

    val uiAction = actions.collectFirst { case a: Action.SetOpenHabItemValue =>
      a
    }
    assert(uiAction.isDefined, "Should produce UI update action")
    assertEquals(uiAction.get.item, confirmedStateUIItem)
    assertEquals(uiAction.get.value, "on") // Switch.On.toStatusString
  }

  test(
    "produceActionsForConfirmed - no inconsistency, sets online and cancels timeout"
  ) {
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.On, Switch.Off, None)

    val actions = producer.produceActionsForConfirmed(remoteState, now)

    val setOnlineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Online")
          if item == inconsistencyUIItem =>
        item
    }
    assert(setOnlineAction.isDefined, "Should set inconsistency item to Online")

    val cancelAction = actions.collectFirst { case a: Action.Cancel => a }
    assert(cancelAction.isDefined, "Should cancel timeout")
    assertEquals(cancelAction.get.id, s"$id-timeout")
  }

  test(
    "produceActionsForConfirmed - recent inconsistency, sets online and schedules timeout"
  ) {
    val inconsistencyStart =
      now.minusSeconds(45) // 45 seconds ago, less than 2 minutes timeout
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.On, Switch.Off, Some(inconsistencyStart))

    val actions = producer.produceActionsForConfirmed(remoteState, now)

    val setOnlineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Online")
          if item == inconsistencyUIItem =>
        item
    }
    assert(setOnlineAction.isDefined, "Should set inconsistency item to Online")

    val delayedAction = actions.collectFirst { case a: Action.Delayed => a }
    assert(delayedAction.isDefined, "Should schedule timeout")
    assertEquals(delayedAction.get.id, s"$id-timeout")

    // Check the delayed action sets offline
    delayedAction.get.action match {
      case Action.SetOpenHabItemValue(item, "Offline")
          if item == inconsistencyUIItem =>
        // Verify delay is approximately correct (timeout - elapsed time)
        val expectedDelay = timeoutInterval - 45.seconds
        assert(
          delayedAction.get.delay.toSeconds >= expectedDelay.toSeconds - 1,
          "Delay should be approximately correct (lower bound)"
        )
        assert(
          delayedAction.get.delay.toSeconds <= expectedDelay.toSeconds + 1,
          "Delay should be approximately correct (upper bound)"
        )
      case _ => fail("Delayed action should set inconsistency item to Offline")
    }
  }

  test(
    "produceActionsForConfirmed - old inconsistency past timeout, sets offline and cancels timeout"
  ) {
    val inconsistencyStart =
      now.minusSeconds(150) // 2.5 minutes ago, past 2 minutes timeout
    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, Some(inconsistencyStart))

    val actions = producer.produceActionsForConfirmed(remoteState, now)

    val setOfflineAction = actions.collectFirst {
      case Action.SetOpenHabItemValue(item, "Offline")
          if item == inconsistencyUIItem =>
        item
    }
    assert(
      setOfflineAction.isDefined,
      "Should set inconsistency item to Offline"
    )

    val cancelAction = actions.collectFirst { case a: Action.Cancel => a }
    assert(cancelAction.isDefined, "Should cancel timeout")
    assertEquals(cancelAction.get.id, s"$id-timeout")
  }

  test("constructor parameters used in all actions") {
    val customProducer = RemoteStateActionProducer(
      confirmedStateUIItem = "custom-ui",
      mqttTopicForCommand = "custom/topic",
      inconsistencyUIItem = "custom-inconsistency",
      id = "custom-id",
      resendInterval = 45.seconds,
      timeoutInterval = 3.minutes
    )

    val remoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.Off, Switch.On, None)

    // Test command actions use custom parameters
    val commandActions =
      customProducer.produceActionsForCommand(remoteState, now)
    val mqttAction = commandActions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(mqttAction.topic, "custom/topic")

    val periodicAction = commandActions.collectFirst {
      case a: Action.Periodic => a
    }.get
    assertEquals(periodicAction.id, "custom-id-command")
    assertEquals(periodicAction.period, 45.seconds)

    val cancelAction = commandActions.collectFirst { case a: Action.Cancel =>
      a
    }.get
    assertEquals(cancelAction.id, "custom-id-timeout")

    // Test confirmed actions use custom parameters
    val confirmedActions =
      customProducer.produceActionsForConfirmed(remoteState, now)
    val uiAction = confirmedActions.collectFirst {
      case Action.SetOpenHabItemValue(item, _) if item == "custom-ui" => item
    }
    assert(uiAction.isDefined, "Should use custom UI item")

    val inconsistencyAction = confirmedActions.collectFirst {
      case Action.SetOpenHabItemValue(item, _)
          if item == "custom-inconsistency" =>
        item
    }
    assert(
      inconsistencyAction.isDefined,
      "Should use custom inconsistency item"
    )
  }

  test("switch status conversion in actions") {
    // Test Switch.Off command
    val offRemoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.On, Switch.Off, None)
    val offActions = producer.produceActionsForCommand(offRemoteState, now)
    val offMqttAction = offActions.collectFirst {
      case a: Action.SendMqttStringMessage => a
    }.get
    assertEquals(offMqttAction.message, "stop") // Switch.Off.toCommandString

    // Test Switch.On confirmed state
    val onRemoteState: RemoteState[Switch.Status] =
      RemoteState(Switch.On, Switch.Off, None)
    val onActions = producer.produceActionsForConfirmed(onRemoteState, now)
    val onUiAction = onActions.collectFirst {
      case a: Action.SetOpenHabItemValue => a
    }.get
    assertEquals(onUiAction.value, "on") // Switch.On.toStatusString
  }

}
