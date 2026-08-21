package calespiga.processor.grid

import calespiga.model.{Action, GridSignal, State}

class GridConnectionManagerStub extends GridConnectionManager {
  var requestCalls: List[GridSignal.ActorsConnecting] = Nil
  var releaseCalls: List[GridSignal.ActorsConnecting] = Nil

  override def requestConnection(
      actor: GridSignal.ActorsConnecting,
      state: State
  ): (State, Set[Action]) = {
    requestCalls = requestCalls :+ actor
    (state, Set.empty)
  }

  override def releaseConnection(
      actor: GridSignal.ActorsConnecting,
      state: State
  ): (State, Set[Action]) = {
    releaseCalls = releaseCalls :+ actor
    (state, Set.empty)
  }

  override def applyConnection(state: State): (State, Set[Action]) =
    (state, Set.empty)
}
