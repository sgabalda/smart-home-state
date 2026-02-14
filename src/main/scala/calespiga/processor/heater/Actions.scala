package calespiga.processor.heater

import calespiga.model.HeaterSignal
import calespiga.config.HeaterConfig
import calespiga.processor.utils.CommandActions

object Actions {

  def apply(
      config: HeaterConfig
  ): CommandActions[HeaterSignal.ControllerState] =
    CommandActions[HeaterSignal.ControllerState](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToPower = _.power
    )

}
