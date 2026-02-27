package calespiga.processor.utils

import calespiga.model.Action
import scala.concurrent.duration.FiniteDuration

/** Generic trait for actions that send commands with resend capability.
  *
  * @tparam Command
  *   The type of command that can be sent
  */
trait CommandActions[Command] {
  def commandActionWithResend(command: Command): Set[Action]
}

object CommandActions {

  val COMMAND_ACTION_SUFFIX = "-command"

  /** Creates a CommandActions instance for a given configuration.
    *
    * @param mqttTopic
    *   The MQTT topic to send commands to
    * @param id
    *   The processor ID used for action identification
    * @param resendInterval
    *   How often to resend the command
    * @param commandToPower
    *   Function to extract power value from command
    * @tparam Command
    *   The type of command (e.g., HeaterSignal.ControllerState)
    * @return
    *   A CommandActions instance
    */
  def apply[Command](
      mqttTopic: String,
      id: String,
      resendInterval: FiniteDuration,
      commandToPower: Command => Int
  ): CommandActions[Command] =
    new CommandActions[Command] {
      private def commandAction(command: Command) =
        Action.SendMqttStringMessage(
          mqttTopic,
          commandToPower(command).toString
        )

      private def periodicCommandAction(command: Command) =
        Action.Periodic(
          id + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          resendInterval
        )

      override def commandActionWithResend(command: Command): Set[Action] =
        Set(commandAction(command), periodicCommandAction(command))
    }
}
