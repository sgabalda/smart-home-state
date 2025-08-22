package calespiga.processor

import calespiga.model.RemoteState
import calespiga.model.RemoteState.*
import java.time.Instant

object RemoteStateProcessor {

  extension [State](state: RemoteState[State]) {
    def process(signal: Signal[State], now: Instant): RemoteState[State] =
      signal match {
        case Event(newState) if state.latestCommand == newState =>
          // the state is set to what was required the last, so we are OK
          RemoteState[State](newState, state.latestCommand, None)
        case Event(newState) =>
          // the state is set, but not to what was requested
          RemoteState[State](
            newState,
            state.latestCommand,
            state.currentInconsistencyStart.orElse(Some(now))
          )
        case c: Command[State] if state.confirmed == c.stateToSet =>
          // a new command is received, but the state is the same as the command requires. store
          // the new command but set no inconsistency
          RemoteState[State](state.confirmed, c.stateToSet, None)
        case c: Command[State] if state.latestCommand == c.stateToSet =>
          // the same command is requested as the last command, and not the current state,
          // so not change anything
          state
        case c: Command[State] =>
          // the command is not the one that was there, and does not align
          // with the current state, so record the inconsistency
          RemoteState[State](state.confirmed, c.stateToSet, Some(now))
      }
  }

}
