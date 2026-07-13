package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

/** This processor is responsible for updating the available power in the state
  * based on grid events. Currently it always applies the confgured available
  * power, but in the future it could be extended to handle dynamic available
  * power based on events.
  */
private object GridAvailablePowerProcessor {

  private final case class Impl(config: GridConfig) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case Event.Grid.GridTariffChanged(tariff) =>
        // in the future, if we have different power available for each tariff,
        // we can update the state with the new available power here
        val newState =
          state.modify(_.grid.availablePower).setTo(Some(config.availablePower))
        (newState, Set.empty)

      case _ => (state, Set.empty)

      // in the future, if we have events reporting the used grid power,
      // we can update the state with the new available power here
    }
  }

  def apply(config: GridConfig): SingleProcessor = Impl(config)
}
