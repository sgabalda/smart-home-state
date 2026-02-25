package calespiga.processor.infraredStove

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.model.Event.InfraredStove.*
import calespiga.model.InfraredStoveSignal
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.processor.ProcessorConfigHelper
import scala.concurrent.duration.*

class InfraredStoveManualTimeProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")

  private val dummyConfig = ProcessorConfigHelper.infraredStoveConfig

  private val manualMaxTimeMinutes = 60

  private def stateWithInfraredStove(
      lastCommandReceived: Option[InfraredStoveSignal.UserCommand] = None,
      lastSetManual: Option[Instant] = None,
      manualMaxTimeMinutes: Option[Int]
  ): State =
    State()
      .modify(_.infraredStove.lastCommandReceived)
      .setTo(lastCommandReceived)
      .modify(_.infraredStove.lastSetManual)
      .setTo(lastSetManual)
      .modify(_.infraredStove.manualMaxTimeMinutes)
      .setTo(manualMaxTimeMinutes)

  private def expectedDelayedStop(delay: FiniteDuration): Action =
    Action.Delayed(
      id = InfraredStoveManualTimeProcessor.DELAY_ID,
      action = Action.SendFeedbackEvent(
        Event.InfraredStove.InfraredStoveManualTimeExpired
      ),
      delay = delay
    )

  // --- InfraredStovePowerCommandChanged ---

  test(
    "InfraredStovePowerCommandChanged: switching from non-manual to manual sets lastSetManual and schedules stop"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.SetPower600),
        now
      )

    assertEquals(newState.infraredStove.lastSetManual, Some(now))
    assertEquals(
      actions,
      Set(expectedDelayedStop(manualMaxTimeMinutes.minutes))
    )
  }

  test(
    "InfraredStovePowerCommandChanged: first ever manual command with no previous command sets lastSetManual and schedules stop"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = None,
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.SetPower1200),
        now
      )

    assertEquals(newState.infraredStove.lastSetManual, Some(now))
    assertEquals(
      actions,
      Set(expectedDelayedStop(manualMaxTimeMinutes.minutes))
    )
  }

  test(
    "InfraredStovePowerCommandChanged: switching from non-manual to manual with no manualMaxTimeMinutes sets lastSetManual but no stop action"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff),
      manualMaxTimeMinutes = None
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.SetPower600),
        now
      )

    assertEquals(newState.infraredStove.lastSetManual, Some(now))
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "InfraredStovePowerCommandChanged: switching from manual to non-manual resets lastSetManual and cancels scheduled stop"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = Some(now.minusSeconds(600)),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.TurnOff),
        now
      )

    assertEquals(newState.infraredStove.lastSetManual, None)
    assertEquals(
      actions,
      Set[Action](Action.Cancel(InfraredStoveManualTimeProcessor.DELAY_ID))
    )
  }

  test(
    "InfraredStovePowerCommandChanged: staying in manual mode keeps lastSetManual unchanged and produces no actions"
  ) {
    val previousLastSetManual = now.minusSeconds(300)
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = Some(previousLastSetManual),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.SetPower1200),
        now
      )

    assertEquals(
      newState.infraredStove.lastSetManual,
      Some(previousLastSetManual)
    )
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "InfraredStovePowerCommandChanged: SetAutomatic resets lastSetManual and cancels scheduled stop"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower1200),
      lastSetManual = Some(now.minusSeconds(120)),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStovePowerCommandChanged(InfraredStoveSignal.SetAutomatic),
        now
      )

    assertEquals(newState.infraredStove.lastSetManual, None)
    assertEquals(
      actions,
      Set[Action](Action.Cancel(InfraredStoveManualTimeProcessor.DELAY_ID))
    )
  }

  // --- StartupEvent ---

  test(
    "StartupEvent: manual mode with time remaining schedules stop with remaining duration"
  ) {
    val elapsedMinutes = 20
    val setAt = now.minusSeconds(elapsedMinutes * 60L)
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = Some(setAt),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    val remainingSeconds = (manualMaxTimeMinutes - elapsedMinutes) * 60L
    assertEquals(newState, initialState)
    assertEquals(actions, Set(expectedDelayedStop(remainingSeconds.seconds)))
  }

  test(
    "StartupEvent: manual mode with elapsed time sends immediate InfraredStoveManualTimeExpired"
  ) {
    val elapsedMinutes = 90
    val setAt = now.minusSeconds(elapsedMinutes * 60L)
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = Some(setAt),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assertEquals(newState, initialState)
    assertEquals(
      actions,
      Set[Action](
        Action.SendFeedbackEvent(
          Event.InfraredStove.InfraredStoveManualTimeExpired
        )
      )
    )
  }

  test(
    "StartupEvent: lastSetManual present but last command is not manual resets lastSetManual"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff),
      lastSetManual = Some(now.minusSeconds(300)),
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assertEquals(newState.infraredStove.lastSetManual, None)
    assertEquals(actions, Set.empty[Action])
  }

  test(
    "StartupEvent: no lastSetManual produces no state change and no actions"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = None,
      manualMaxTimeMinutes = Some(manualMaxTimeMinutes)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(initialState, Event.System.StartupEvent, now)

    assertEquals(newState, initialState)
    assertEquals(actions, Set.empty[Action])
  }

  // --- InfraredStoveManualTimeChanged ---

  test(
    "InfraredStoveManualTimeChanged: updates manualMaxTimeMinutes in state"
  ) {
    val initialState = stateWithInfraredStove(manualMaxTimeMinutes = None)
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, _) =
      processor.process(
        initialState,
        InfraredStoveManualTimeChanged(30),
        now
      )

    assertEquals(newState.infraredStove.manualMaxTimeMinutes, Some(30))
  }

  test(
    "InfraredStoveManualTimeChanged: stove in manual mode with time remaining reschedules stop with remaining time"
  ) {
    val elapsedMinutes = 10
    val newMaxMinutes = 60
    val setAt = now.minusSeconds(elapsedMinutes * 60L)
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower1200),
      lastSetManual = Some(setAt),
      manualMaxTimeMinutes = Some(30)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStoveManualTimeChanged(newMaxMinutes),
        now
      )

    val remainingSeconds = (newMaxMinutes - elapsedMinutes) * 60L
    assertEquals(
      newState.infraredStove.manualMaxTimeMinutes,
      Some(newMaxMinutes)
    )
    assertEquals(actions, Set(expectedDelayedStop(remainingSeconds.seconds)))
  }

  test(
    "InfraredStoveManualTimeChanged: stove in manual mode but new threshold already elapsed cancels stop"
  ) {
    val elapsedMinutes = 90
    val newMaxMinutes = 30
    val setAt = now.minusSeconds(elapsedMinutes * 60L)
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.SetPower600),
      lastSetManual = Some(setAt),
      manualMaxTimeMinutes = Some(120)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStoveManualTimeChanged(newMaxMinutes),
        now
      )

    assertEquals(
      newState.infraredStove.manualMaxTimeMinutes,
      Some(newMaxMinutes)
    )
    assertEquals(
      actions,
      Set[Action](Action.Cancel(InfraredStoveManualTimeProcessor.DELAY_ID))
    )
  }

  test(
    "InfraredStoveManualTimeChanged: stove not in manual mode only updates manualMaxTimeMinutes, no actions"
  ) {
    val initialState = stateWithInfraredStove(
      lastCommandReceived = Some(InfraredStoveSignal.TurnOff),
      lastSetManual = None,
      manualMaxTimeMinutes = Some(30)
    )
    val processor = InfraredStoveManualTimeProcessor(dummyConfig)
    val (newState, actions) =
      processor.process(
        initialState,
        InfraredStoveManualTimeChanged(60),
        now
      )

    assertEquals(newState.infraredStove.manualMaxTimeMinutes, Some(60))
    assertEquals(actions, Set.empty[Action])
  }
}
