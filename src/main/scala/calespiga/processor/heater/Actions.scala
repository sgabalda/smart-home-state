package calespiga.processor.heater

import calespiga.model.Action
import calespiga.model.HeaterSignal
import calespiga.config.HeaterConfig

trait Actions {
  def commandActionWithResend(
      command: HeaterSignal.ControllerState
  ): Set[Action]
}

object Actions {

  val COMMAND_ACTION_SUFFIX = "-command"

  def apply(config: HeaterConfig): Actions =
    new Actions {
      private def commandAction(command: HeaterSignal.ControllerState) =
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          command.power.toString
        )
      private def periodicCommandAction(
          command: HeaterSignal.ControllerState
      ) = {
        Action.Periodic(
          config.id + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          config.resendInterval
        )
      }
      override def commandActionWithResend(
          command: HeaterSignal.ControllerState
      ) = {
        Set(commandAction(command), periodicCommandAction(command))
      }
    }
}
