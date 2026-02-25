package calespiga.processor.infraredStove

import calespiga.model.InfraredStoveSignal
import calespiga.config.InfraredStoveConfig
import calespiga.processor.utils.CommandActions
import calespiga.model.InfraredStoveSignal.*

private object Actions {

  private[infraredStove] def isManualCommand(c: UserCommand): Boolean =
    c match
      case TurnOff      => false
      case SetAutomatic => false
      case SetPower600  => true
      case SetPower1200 => true

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
