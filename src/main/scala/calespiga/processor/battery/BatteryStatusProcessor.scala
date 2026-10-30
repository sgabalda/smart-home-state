package calespiga.processor.battery

import calespiga.config.BatteryConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.State
import calespiga.processor.SingleProcessor
import com.softwaremill.quicklens.*
import java.time.Instant

private[battery] object BatteryStatusProcessor {

  private final case class Impl(
      config: BatteryConfig
  ) extends SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match {

        case Event.Battery.BatteryStatusReported(status) =>
          val newState = state.modify(_.battery.status).setTo(Some(status))
          val actions: Set[Action] =
            Set(Action.SetUIItemValue(config.statusItem, status.label))
          (newState, actions)

        case Event.System.StartupEvent =>
          val actions: Set[Action] = state.battery.status
            .map(status =>
              Action.SetUIItemValue(config.statusItem, status.label)
            )
            .toSet
          (state, actions)

        case _ =>
          (state, Set.empty)
      }
  }

  def apply(config: BatteryConfig): SingleProcessor = Impl(config)
}
