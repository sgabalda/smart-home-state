package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[carCharger] object CarChargerGridTariffProcessor {

  private final case class Impl(config: CarChargerConfig)
      extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {
      case Event.CarCharger.CarChargerMaxGridTariffChanged(tariff) =>
        val newState =
          state.modify(_.carCharger.maxGridTariff).setTo(Some(tariff))
        (newState, Set.empty)

      case Event.System.StartupEvent =>
        val actions: Set[Action] = state.carCharger.maxGridTariff
          .map(tariff =>
            Action.SetUIItemValue(config.maxGridTariffItem, tariff.label)
          )
          .toSet
        (state, actions)

      case _ => (state, Set.empty)
    }
  }

  def apply(config: CarChargerConfig): SingleProcessor = Impl(config)
}
