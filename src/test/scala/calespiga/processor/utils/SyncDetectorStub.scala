package calespiga.processor.utils

import calespiga.model.{State, Event, Action}
import java.time.Instant
import calespiga.processor.utils.SyncDetector.CheckSyncResult

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
