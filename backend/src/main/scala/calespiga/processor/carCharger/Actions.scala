package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.CarChargerSignal
import calespiga.processor.utils.CommandActions

object Actions {

  def apply(
      config: CarChargerConfig
  ): CommandActions[CarChargerSignal.ControllerState] =
    CommandActions[CarChargerSignal.ControllerState](
      mqttTopic = config.mqttTopicForCommand,
      id = config.id,
      resendInterval = config.resendInterval,
      commandToMessage = CarChargerSignal.controllerStateToString
    )

}
