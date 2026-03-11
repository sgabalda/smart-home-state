package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private object GridTariffProcessor {

  private final case class Impl(config: GridConfig) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case Event.Grid.GridTariffChanged(tariff) =>
        val newState = state.modify(_.grid.currentTariff).setTo(Some(tariff))
        (newState, Set(Action.SetUIItemValue(config.tariffItem, tariff.label)))

      case Event.System.StartupEvent =>
        val actions: Set[Action] = state.grid.currentTariff
          .map(t => Action.SetUIItemValue(config.tariffItem, t.label))
          .toSet
        (state, actions)

      case _ => (state, Set.empty)
    }
  }

  def apply(config: GridConfig): SingleProcessor = Impl(config)
}
