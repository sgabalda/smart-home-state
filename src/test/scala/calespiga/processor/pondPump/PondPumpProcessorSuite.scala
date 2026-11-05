package calespiga.processor.pondPump

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.PondPump.StatusReported
import calespiga.model.Event.Pump.CommandRequested
import calespiga.model.OfflineOnlineSignal
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.OfflineDetector
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import munit.FunSuite

class PondPumpProcessorSuite extends FunSuite {

  private val config = ProcessorConfigHelper.pondPumpConfig
  private val processor = PondPumpProcessor(
    config,
    ProcessorConfigHelper.offlineDetectorConfig,
    ProcessorConfigHelper.syncDetectorConfig
  )
  private val now = java.time.Instant.parse("2023-08-17T10:00:00Z")
  private val commandId = config.id + "-command"
  private val offlineId = config.id + OfflineDetector.ID_SUFFIX
  private val syncId = config.id + SyncDetector.ID_SUFFIX
  private val onCommand = Action.SendMqttStringMessage(
    config.mqttTopicForCommand,
    "start"
  )

  test("matching command requests send and periodically resend pump command") {
    val (newState, actions) =
      processor.process(
        State(),
        CommandRequested(config.id, on = true),
        now
      )

    assertEquals(newState.pondPump.lastCommandSent, Some(true))
    assertEquals(
      actions,
      Set(
        onCommand,
        Action.Periodic(commandId, onCommand, config.resendInterval),
        Action.SetUIItemValue(config.syncStatusItem, "SYNCING"),
        Action.Delayed(
          syncId,
          Action.SetUIItemValue(
            config.syncStatusItem,
            ProcessorConfigHelper.syncDetectorConfig.nonSyncText
          ),
          ProcessorConfigHelper.syncDetectorConfig.timeoutDuration
        )
      )
    )
  }

  test("commands for another pump are ignored") {
    val (newState, actions) =
      processor.process(
        State(),
        CommandRequested("water-tank-pump", on = true, force = true),
        now
      )

    assertEquals(newState, State())
    assertEquals(actions, Set.empty)
  }

  test("status reports update pump state and mark its controller online") {
    val (newState, actions) =
      processor.process(State(), StatusReported("on"), now)

    assertEquals(newState.pondPump.status, Some(true))
    assertEquals(
      newState.pondPump.online,
      Some(OfflineOnlineSignal.Online)
    )
    assert(actions.contains(Action.SetUIItemValue(config.statusItem, "on")))
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.onlineStatusItem,
          ProcessorConfigHelper.offlineDetectorConfig.onlineText
        )
      )
    )
    assert(
      actions.contains(
        Action.Delayed(
          offlineId,
          Action.SendFeedbackEvent(Event.System.OfflineDetected(offlineId)),
          ProcessorConfigHelper.offlineDetectorConfig.timeoutDuration
        )
      )
    )
  }

  test("a matching status report clears the syncing state") {
    val requestedState = State()
      .modify(_.pondPump.lastCommandSent)
      .setTo(Some(true))
      .modify(_.pondPump.lastSyncing)
      .setTo(Some(now.minusSeconds(30)))
    val (newState, actions) =
      processor.process(requestedState, StatusReported("on"), now)

    assertEquals(newState.pondPump.status, Some(true))
    assertEquals(newState.pondPump.lastSyncing, None)
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.syncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncText
        )
      )
    )
    assert(actions.contains(Action.Cancel(syncId)))
  }

  test("invalid status reports do not count as online or change pump state") {
    val (newState, actions) =
      processor.process(State(), StatusReported("running"), now)

    assertEquals(newState, State())
    assertEquals(actions, Set.empty)
  }

  test("offline timeout marks the pond-pump controller offline") {
    val (newState, actions) =
      processor.process(
        State(),
        Event.System.OfflineDetected(offlineId),
        now
      )

    assertEquals(
      newState.pondPump.online,
      Some(OfflineOnlineSignal.Offline)
    )
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          config.onlineStatusItem,
          ProcessorConfigHelper.offlineDetectorConfig.offlineText
        )
      )
    )
  }
}
