package calespiga.processor.pondPump

import calespiga.config.PondPumpConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.PondPump.HeightReported
import calespiga.model.Event.PondPump.LitersReported
import calespiga.model.Event.PondPump.PercentageReported
import calespiga.model.State
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*

private[pondPump] object PondMeasurementsProcessor {

  private final case class Impl(config: PondPumpConfig)
      extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: java.time.Instant
    ): (State, Set[Action]) = eventData match {
      case PercentageReported(percentage) =>
        (
          state.modify(_.pond.percentage).setTo(Some(percentage)),
          Set(Action.SetUIItemValue(config.percentageItem, percentage.toString))
        )

      case LitersReported(liters) =>
        (
          state.modify(_.pond.liters).setTo(Some(liters)),
          Set(Action.SetUIItemValue(config.litersItem, liters.toString))
        )

      case HeightReported(height) =>
        (
          state.modify(_.pond.height).setTo(Some(height)),
          Set(Action.SetUIItemValue(config.heightItem, height.toString))
        )

      case _ => (state, Set.empty)
    }
  }

  def apply(config: PondPumpConfig): SingleProcessor = Impl(config)
}
