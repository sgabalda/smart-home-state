package calespiga.processor

import calespiga.model.{State, Action, Event}
import java.time.Instant

class HeaterProcessor extends StateProcessor.SingleProcessor {
  override def process(
      state: State,
      eventData: Event.EventData,
      timestamp: Instant
  ): (State, Set[Action]) = eventData match {
    case hd: Event.Heater.HeaterData =>
      // TODO: handle HeaterData events in next steps
      (state, Set.empty)
    case _ =>
      (state, Set.empty)
  }
}
