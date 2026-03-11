package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.model.GridSignal
import calespiga.processor.utils.CommandActions

/** Produces MQTT actions for the grid connection relay.
  *
  * The relay does not use a numeric power level; instead commands are sent as
  * "start" (connect) or "stop" (disconnect) strings.
  */
private object Actions {

  def apply(
      config: GridConfig
  ): CommandActions[GridSignal.ControllerState] =
    CommandActions[GridSignal.ControllerState](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToMessage = GridSignal.toMqttCommand
    )
}
