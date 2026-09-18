package calespiga.processor.utils

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.State
import calespiga.processor.utils.SyncDetector.CheckSyncResult
import java.time.Instant

object SyncDetectorStub {

  def apply(
      processStub: (State, Event.EventData, Instant) => (State, Set[Action]) =
        (state, _, _) => (state, Set.empty),
      checkIfInSyncStub: State => CheckSyncResult = _ => SyncDetector.InSync
  ): SyncDetector = new SyncDetector {
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      processStub(state, eventData, timestamp)

    override def checkIfInSync(state: State): CheckSyncResult =
      checkIfInSyncStub(state)
  }
}
