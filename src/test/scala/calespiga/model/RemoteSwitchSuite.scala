package calespiga.model

import munit.FunSuite
import java.time.Instant
import calespiga.processor.RemoteStateProcessor.*

class RemoteSwitchSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val later = Instant.parse("2023-08-17T10:01:00Z")

  test("initial state creation") {
    val remoteState = RemoteState(Switch.Off, Switch.Off, None)

    assertEquals(remoteState.confirmed, Switch.Off)
    assertEquals(remoteState.latestCommand, Switch.Off)
    assertEquals(remoteState.currentInconsistencyStart, None)
  }

  test("process Event that matches latest command - inconsistency resolved") {
    val initialState = RemoteState(Switch.Off, Switch.On, Some(now))

    val result = initialState.process(RemoteState.Event(Switch.On), later)

    // When event matches latestCommand, state becomes consistent
    assertEquals(result.confirmed, Switch.On)
    assertEquals(result.latestCommand, Switch.On)
    assertEquals(result.currentInconsistencyStart, None)
  }

  test(
    "process Event that does not match latest command - inconsistency starts"
  ) {
    val initialState = RemoteState[Switch.Status](Switch.On, Switch.On, None)

    val result = initialState.process(RemoteState.Event(Switch.Off), now)

    // When event doesn't match latestCommand, inconsistency starts
    assertEquals(result.confirmed, Switch.Off)
    assertEquals(result.latestCommand, Switch.On)
    assertEquals(result.currentInconsistencyStart, Some(now))
  }

  test(
    "process Event that does not match latest command - inconsistency continues"
  ) {
    val inconsistencyStart = now
    val initialState =
      RemoteState(Switch.Off, Switch.On, Some(inconsistencyStart))

    val result = initialState.process(RemoteState.Event(Switch.Off), later)

    // When already inconsistent, preserve original timestamp
    assertEquals(result.confirmed, Switch.Off)
    assertEquals(result.latestCommand, Switch.On)
    assertEquals(
      result.currentInconsistencyStart,
      Some(inconsistencyStart)
    ) // Original timestamp preserved
  }

  test(
    "process Command that matches current confirmed state - no inconsistency"
  ) {
    val initialState = RemoteState(Switch.On, Switch.Off, Some(now))

    val result = initialState.process(RemoteState.Command(Switch.On), later)

    // Command matches confirmed state, so no inconsistency
    assertEquals(result.confirmed, Switch.On)
    assertEquals(result.latestCommand, Switch.On) // Latest command changed
    assertEquals(result.currentInconsistencyStart, None)
  }

  test("process same Command as latest command - no change") {
    val initialState = RemoteState(Switch.Off, Switch.On, Some(now))

    val result = initialState.process(RemoteState.Command(Switch.On), later)

    // Same command as latest, no change
    assertEquals(result, initialState) // No change at all
  }

  test(
    "process new Command different from confirmed state - inconsistency starts"
  ) {
    val initialState = RemoteState[Switch.Status](Switch.Off, Switch.Off, None)

    val result = initialState.process(RemoteState.Command(Switch.On), now)

    // New command different from confirmed state -> inconsistency
    assertEquals(result.confirmed, Switch.Off)
    assertEquals(result.latestCommand, Switch.On) // Latest command unchanged
    assertEquals(result.currentInconsistencyStart, Some(now))
  }

  test(
    "process new Command different from confirmed state - existing inconsistency preserved"
  ) {
    val originalInconsistency = now
    val initialState =
      RemoteState(Switch.Off, Switch.On, Some(originalInconsistency))

    val result = initialState.process(RemoteState.Command(Switch.On), later)

    // New command, but inconsistency timestamp is preserved
    assertEquals(result.confirmed, Switch.Off)
    assertEquals(result.latestCommand, Switch.On) // Latest command unchanged
    assertEquals(
      result.currentInconsistencyStart,
      Some(originalInconsistency)
    ) // Original timestamp preserved
  }
}
