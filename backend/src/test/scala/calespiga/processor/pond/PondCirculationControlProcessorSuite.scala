package calespiga.processor.pond

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.PondCirculation.*
import calespiga.model.PondCirculationCommand
import calespiga.model.PondCirculationMode
import calespiga.model.PondCirculationStatus
import calespiga.model.State
import calespiga.processor.ProcessorConfigHelper
import calespiga.processor.utils.CommandActions
import calespiga.processor.utils.SyncDetector
import com.softwaremill.quicklens.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import java.time.Instant
import java.time.ZoneId
import munit.FunSuite
import scala.concurrent.duration.*

class PondCirculationControlProcessorSuite extends FunSuite {

  private val config = ProcessorConfigHelper.pondPumpConfig
  private val processor =
    PondCirculationControlProcessor(config, ZoneId.of("UTC"))
  private val processorWithSync = processor.andThen(
    PondCirculationSyncDetector(
      ProcessorConfigHelper.syncDetectorConfig,
      config.id + "-circulation",
      config.circulationSyncStatusItem
    )
  )
  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val commandId =
    config.id + "-circulation" + CommandActions.COMMAND_ACTION_SUFFIX

  private def expectedCommand(command: String): Set[Action] = {
    val mqttAction = Action.SendMqttStringMessage(
      config.circulationMqttTopicForCommand,
      command
    )
    Set(
      mqttAction,
      Action.Periodic(commandId, mqttAction, config.resendInterval)
    )
  }

  private def scheduledTransition(delay: FiniteDuration): Action =
    Action.Delayed(
      PondCirculationControlProcessor.SCHEDULE_ID,
      Action.SendFeedbackEvent(ScheduleTransition),
      delay
    )

  test(
    "startup starts depuradora for the selected cadence and displays settings"
  ) {
    val (newState, actions) =
      processor.process(State(), Event.System.StartupEvent, now)

    assertEquals(
      newState.pondCirculation.lastCommandSent,
      Some(PondCirculationCommand.Depuradora)
    )
    assert(expectedCommand("depuradora").subsetOf(actions))
    assert(actions.contains(scheduledTransition(3.minutes)))
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.circulationScheduleDescriptionItem,
          "Encesa de 10 a 17, 30% del temps"
        )
      )
    )
  }

  test("cadence turns depuradora off after the selected minutes of each slot") {
    val state = State()
      .modify(_.pondCirculation.lastCommandSent)
      .setTo(Some(PondCirculationCommand.Depuradora))
    val (newState, actions) = processor.process(
      state,
      ScheduleTransition,
      now.plus(3, java.time.temporal.ChronoUnit.MINUTES)
    )

    assertEquals(
      newState.pondCirculation.lastCommandSent,
      Some(PondCirculationCommand.Stop)
    )
    assert(expectedCommand("stop").subsetOf(actions))
    assert(actions.contains(scheduledTransition(7.minutes)))
  }

  test("schedule turns off at cadence cutoff and schedules the next cycle") {
    val state = State()
      .modify(_.pondCirculation.cadencePercentage)
      .setTo(10)
      .modify(_.pondCirculation.lastCommandSent)
      .setTo(Some(PondCirculationCommand.Depuradora))
    val (_, actions) =
      processor.process(state, ScheduleTransition, now.plusSeconds(5 * 60))

    assert(expectedCommand("stop").subsetOf(actions))
    assert(actions.contains(scheduledTransition(5.minutes)))
  }

  test("full cadence schedules the end of the inclusive end hour") {
    val state = State()
      .modify(_.pondCirculation.cadencePercentage)
      .setTo(100)
    val (_, actions) = processor.process(
      state,
      Event.System.StartupEvent,
      Instant.parse("2023-08-17T16:42:00Z")
    )

    assert(expectedCommand("depuradora").subsetOf(actions))
    assert(actions.contains(scheduledTransition(78.minutes)))
  }

  test("the end hour is inclusive, including the final ten-minute cycle") {
    val (onState, onActions) = processor.process(
      State(),
      Event.System.StartupEvent,
      Instant.parse("2023-08-17T17:50:00Z")
    )
    assertEquals(
      onState.pondCirculation.lastCommandSent,
      Some(PondCirculationCommand.Depuradora)
    )
    assert(expectedCommand("depuradora").subsetOf(onActions))
    assert(onActions.contains(scheduledTransition(3.minutes)))

    val (_, offActions) = processor.process(
      onState,
      ScheduleTransition,
      Instant.parse("2023-08-17T17:53:00Z")
    )
    assert(expectedCommand("stop").subsetOf(offActions))
    assert(offActions.contains(scheduledTransition(16.hours + 7.minutes)))
  }

  test("transvassament selects diposit and suspends the depuradora schedule") {
    val (newState, actions) =
      processor.process(State(), ModeChanged("diposit"), now)

    assertEquals(
      newState.pondCirculation.selectedMode,
      PondCirculationMode.Diposit
    )
    assert(expectedCommand("diposit").subsetOf(actions))
    assert(
      actions.contains(
        Action.Cancel(PondCirculationControlProcessor.SCHEDULE_ID)
      )
    )
    assert(!actions.exists(_.isInstanceOf[Action.Delayed]))
  }

  test(
    "transvassament remains selected across startup outside schedule hours"
  ) {
    val initialState = State()
      .modify(_.pondCirculation.selectedMode)
      .setTo(PondCirculationMode.Diposit)
    val (_, actions) = processor.process(
      initialState,
      Event.System.StartupEvent,
      Instant.parse("2023-08-17T02:00:00Z")
    )

    assert(expectedCommand("diposit").subsetOf(actions))
    assert(
      actions.contains(
        Action.Cancel(PondCirculationControlProcessor.SCHEDULE_ID)
      )
    )
  }

  test("invalid cadence values restore the configured value") {
    val (newState, actions) =
      processor.process(State(), CadenceChanged(35), now)

    assertEquals(newState, State())
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(config.circulationCadenceItem, "30")
      )
    )
  }

  test("all firmware circulation statuses are stored and displayed verbatim") {
    val statuses = List(
      "preparing_diposit",
      "diposit",
      "preparing_depuradora",
      "depuradora",
      "preparing_off",
      "off"
    )

    statuses.foreach { rawStatus =>
      val (newState, actions) =
        processor.process(State(), StatusReported(rawStatus), now)
      assertEquals(
        newState.pondCirculation.status,
        PondCirculationStatus.fromString(rawStatus)
      )
      assertEquals(
        actions,
        Set[Action](
          Action.SetUIItemValue(config.circulationStatusItem, rawStatus)
        )
      )
    }
  }

  test("circulation commands and status reports update the sync indicator") {
    val (commandState, commandActions) =
      processorWithSync.process(State(), ModeChanged("diposit"), now)

    assert(
      commandActions.contains(
        Action.SetUIItemValue(
          config.circulationSyncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncingText
        )
      )
    )
    assertEquals(commandState.pondCirculation.lastSyncing, Some(now))

    val (syncedState, statusActions) = processorWithSync.process(
      commandState,
      StatusReported("diposit"),
      now.plusSeconds(5)
    )

    assertEquals(syncedState.pondCirculation.lastSyncing, None)
    assert(
      statusActions.contains(
        Action.SetUIItemValue(
          config.circulationSyncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncText
        )
      )
    )
    assert(
      statusActions.contains(
        Action.Cancel(
          config.id + "-circulation" + SyncDetector.ID_SUFFIX
        )
      )
    )
  }

  test("transitional circulation status remains out of sync") {
    val state = State()
      .modify(_.pondCirculation.lastCommandSent)
      .setTo(Some(PondCirculationCommand.Diposit))

    val (newState, actions) =
      processorWithSync.process(state, StatusReported("preparing_diposit"), now)

    assertEquals(newState.pondCirculation.lastSyncing, Some(now))
    assert(
      actions.contains(
        Action.SetUIItemValue(
          config.circulationSyncStatusItem,
          ProcessorConfigHelper.syncDetectorConfig.syncingText
        )
      )
    )
  }

  test("invalid circulation statuses are ignored") {
    val (newState, actions) =
      processor.process(State(), StatusReported("running"), now)

    assertEquals(newState, State())
    assertEquals(actions, Set.empty[Action])
  }

  test("circulation state persists and legacy state gets schedule defaults") {
    val state = State()
      .modify(_.pondCirculation.status)
      .setTo(Some(PondCirculationStatus.PreparingDepuradora))
      .modify(_.pondCirculation.lastCommandSent)
      .setTo(Some(PondCirculationCommand.Depuradora))

    assertEquals(decode[State](state.asJson.noSpaces), Right(state))
    assertEquals(decode[State]("{}"), Right(State()))
  }
}
