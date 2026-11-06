package calespiga.processor.pondPump

import calespiga.config.PondPumpConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.PondPump.StatusReported
import calespiga.model.Event.Pump.CommandRequested
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*

private[pondPump] object PondPumpControlProcessor {

  private final case class Impl(config: PondPumpConfig)
      extends SingleProcessor {

    private val commands = CommandActions[Boolean](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToMessage = pumpOn => if pumpOn then "start" else "stop"
    )

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: java.time.Instant
    ): (State, Set[Action]) = eventData match {
      case CommandRequested(pumpId, pumpOn, force)
          if pumpId == config.id &&
            (force || state.pondPump.lastCommandSent != Some(pumpOn)) =>
        (
          state.modify(_.pondPump.lastCommandSent).setTo(Some(pumpOn)),
          commands.commandActionWithResend(pumpOn)
        )

      case StatusReported(rawStatus) =>
        rawStatus.trim.toLowerCase match {
          case "on" | "off" =>
            val pumpOn = rawStatus.trim.equalsIgnoreCase("on")
            (
              state.modify(_.pondPump.status).setTo(Some(pumpOn)),
              Set(
                Action.SetUIItemValue(
                  config.statusItem,
                  if pumpOn then "on" else "off"
                )
              )
            )
          case _ => (state, Set.empty)
        }

      case _ => (state, Set.empty)
    }
  }

  def apply(config: PondPumpConfig): SingleProcessor = Impl(config)
}
