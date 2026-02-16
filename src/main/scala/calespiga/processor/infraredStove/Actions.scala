package calespiga.processor.infraredStove

import calespiga.model.InfraredStoveSignal
import calespiga.config.InfraredStoveConfig
import calespiga.processor.utils.CommandActions

private object Actions {

  def apply(
      config: InfraredStoveConfig
  ): CommandActions[InfraredStoveSignal.ControllerState] =
    CommandActions[InfraredStoveSignal.ControllerState](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToPower = _.power
    )

}
