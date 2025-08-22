package calespiga.processor

import munit.FunSuite
import java.time.Instant
import calespiga.processor.RemoteStateProcessor.*
import calespiga.model.RemoteState

class RemoteStateSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val later = Instant.parse("2023-08-17T10:01:00Z")

  sealed trait TestState
  case object TestStateA extends TestState
  case object TestStateB extends TestState
  case object TestStateC extends TestState

  test("initial state creation") {
    val remoteState = RemoteState(TestStateA, TestStateA, None)

    assertEquals(remoteState.confirmed, TestStateA)
    assertEquals(remoteState.latestCommand, TestStateA)
    assertEquals(remoteState.currentInconsistencyStart, None)
  }

  test("process Event that matches latest command - inconsistency resolved") {
    val initialState = RemoteState(TestStateA, TestStateB, Some(now))

    val result = initialState.process(RemoteState.Event(TestStateB), later)

    // When event matches latestCommand, state becomes consistent
    assertEquals(result.confirmed, TestStateB)
    assertEquals(result.latestCommand, TestStateB)
    assertEquals(result.currentInconsistencyStart, None)
  }

  test(
    "process Event that does not match latest command - inconsistency starts"
  ) {
    val initialState = RemoteState[TestState](TestStateA, TestStateA, None)

    val result = initialState.process(RemoteState.Event(TestStateB), now)

    // When event doesn't match latestCommand, inconsistency starts
    assertEquals(result.confirmed, TestStateB)
    assertEquals(result.latestCommand, TestStateA)
    assertEquals(result.currentInconsistencyStart, Some(now))
  }

  test(
    "process Event that does not match latest command - inconsistency continues"
  ) {
    val inconsistencyStart = now
    val initialState =
      RemoteState(TestStateA, TestStateB, Some(inconsistencyStart))

    val result = initialState.process(RemoteState.Event(TestStateC), later)

    // When already inconsistent, preserve original timestamp
    assertEquals(result.confirmed, TestStateC)
    assertEquals(result.latestCommand, TestStateB)
    assertEquals(
      result.currentInconsistencyStart,
      Some(inconsistencyStart)
    ) // Original timestamp preserved
  }

  test(
    "process Command that matches current confirmed state - no inconsistency"
  ) {
    val initialState = RemoteState(TestStateB, TestStateC, Some(now))

    val result = initialState.process(RemoteState.Command(TestStateB), later)

    // Command matches confirmed state, so no inconsistency
    assertEquals(result.confirmed, TestStateB)
    assertEquals(result.latestCommand, TestStateB) // Latest command changed
    assertEquals(result.currentInconsistencyStart, None)
  }

  test("process same Command as latest command - no change") {
    val initialState = RemoteState(TestStateA, TestStateB, Some(now))

    val result = initialState.process(RemoteState.Command(TestStateB), later)

    // Same command as latest, no change
    assertEquals(result, initialState) // No change at all
  }

  test(
    "process new Command different from confirmed state - inconsistency starts"
  ) {
    val initialState = RemoteState[TestState](TestStateA, TestStateA, None)

    val result = initialState.process(RemoteState.Command(TestStateB), now)

    // New command different from confirmed state -> inconsistency
    assertEquals(result.confirmed, TestStateA)
    assertEquals(result.latestCommand, TestStateB)
    assertEquals(result.currentInconsistencyStart, Some(now))
  }

  test(
    "process new Command different from confirmed state but equal to last - existing inconsistency preserved"
  ) {
    val originalInconsistency = now
    val initialState =
      RemoteState(TestStateA, TestStateB, Some(originalInconsistency))

    val result = initialState.process(RemoteState.Command(TestStateB), later)

    // New command, as is the same as before inconsistency timestamp is preserved
    assertEquals(result.confirmed, TestStateA)
    assertEquals(result.latestCommand, TestStateB)
    assertEquals(
      result.currentInconsistencyStart,
      Some(originalInconsistency)
    ) // same inconsistency time
  }

  test(
    "process new Command different from confirmed state and different from last - new inconsistency"
  ) {
    val originalInconsistency = now
    val initialState =
      RemoteState(TestStateA, TestStateB, Some(originalInconsistency))

    val result = initialState.process(RemoteState.Command(TestStateC), later)

    // New command, timestamp is updated
    assertEquals(result.confirmed, TestStateA)
    assertEquals(result.latestCommand, TestStateC)
    assertEquals(
      result.currentInconsistencyStart,
      Some(later)
    ) // different inconsistency
  }
}
