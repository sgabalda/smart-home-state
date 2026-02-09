package calespiga.processor.infraredStove

import calespiga.model.Action
import calespiga.model.InfraredStoveSignal
import calespiga.config.InfraredStoveConfig

trait Actions {
  def commandActionWithResend(
      command: InfraredStoveSignal.ControllerState
  ): Set[Action]
}

object Actions {

  val COMMAND_ACTION_SUFFIX = "-command"

  def apply(config: InfraredStoveConfig): Actions =
    new Actions {
      private def commandAction(command: InfraredStoveSignal.ControllerState) =
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          command.power.toString
        )
      private def periodicCommandAction(
          command: InfraredStoveSignal.ControllerState
      ) = {
        Action.Periodic(
          config.id + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          config.resendInterval
        )
      }
      override def commandActionWithResend(
          command: InfraredStoveSignal.ControllerState
      ) = {
        Set(commandAction(command), periodicCommandAction(command))
      }
    }
}
