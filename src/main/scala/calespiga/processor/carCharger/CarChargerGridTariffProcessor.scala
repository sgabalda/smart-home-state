package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{Action, Event, State}
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

/** Processor for managing the maximum grid tariff configuration for car charger
  * charging.
  *
  * The car charger's `automatic_grid` mode allows charging using both solar FV
  * and grid power. This processor manages the user-configured tariff preference
  * (maxGridTariff).
  *
  * **Default Behavior**: When maxGridTariff is None (default state), the car
  * charger will NOT charge in automatic_grid mode because gridTariffAllowed()
  * requires an explicit tariff preference to be set. Users must set a tariff
  * preference via the UI before grid charging will function:
  *   - "vall" - allow charging only during valley/night tariff
  *   - "pla + vall" - allow charging during off-peak and valley tariffs
  *   - "all tariffs" - allow charging anytime when power is available
  *
  * This ensures grid charging is not accidentally enabled for expensive peak
  * hours.
  */
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
