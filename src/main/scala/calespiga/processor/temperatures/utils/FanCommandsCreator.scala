package calespiga.processor.temperatures.utils

import calespiga.model.FanSignal
import calespiga.model.State
import calespiga.model.FanSignal.*
import calespiga.model.Action
import scala.concurrent.duration.FiniteDuration

trait FanCommandsCreator {

  def commandToSend(
      command: FanSignal.UserCommand,
      state: State
  ): FanSignal.ControllerState

  def commandActionWithResend(command: FanSignal.ControllerState): Set[Action]

}

object FanCommandsCreator {

  val COMMAND_ACTION_SUFFIX = "-command"

  private case class Impl(
      temperatureExtractor: State => Option[Double],
      fanId: String,
      resendInterval: FiniteDuration,
      mqttTopic: String
  ) extends FanCommandsCreator {

    private def automaticCommand(
        goalTemp: Double,
        currentTemp: Option[Double],
        externalTemp: Option[Double]
    ): FanSignal.ControllerState =
      (currentTemp, externalTemp) match
        case (Some(current), Some(external)) =>
          if current > goalTemp && current > external then FanSignal.On
          else if current < goalTemp && current < external then FanSignal.On
          else FanSignal.Off
        case _ => FanSignal.Off

    override def commandToSend(
        command: FanSignal.UserCommand,
        state: State
    ): FanSignal.ControllerState = command match
      case TurnOff      => FanSignal.Off
      case TurnOn       => FanSignal.On
      case SetAutomatic =>
        automaticCommand(
          state.temperatures.goalTemperature,
          temperatureExtractor(state),
          state.temperatures.externalTemperature
        )

    private def commandAction(
        command: FanSignal.ControllerState
    ) =
      Action.SendMqttStringMessage(
        mqttTopic,
        FanSignal.controllerStateToCommand(command)
      )

    private def periodicCommandAction(
        command: FanSignal.ControllerState
    ) = {
      Action.Periodic(
        fanId + COMMAND_ACTION_SUFFIX,
        commandAction(command),
        resendInterval
      )
    }

    def commandActionWithResend(
        command: FanSignal.ControllerState
    ): Set[Action] = {
      Set(
        commandAction(command),
        periodicCommandAction(command)
      )
    }
  }

  def apply(
      temperatureExtractor: State => Option[Double],
      fanId: String,
      resendInterval: FiniteDuration,
      mqttTopic: String
  ): FanCommandsCreator =
    new Impl(temperatureExtractor, fanId, resendInterval, mqttTopic)
}
