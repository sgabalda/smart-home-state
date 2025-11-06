package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.model.Event
import calespiga.model.Event.Temperature.Fans.ElectronicsFanStatus
import calespiga.model.Event.Temperature.Fans.ElectronicsFanCommand
import calespiga.model.State
import calespiga.model.Action
import com.softwaremill.quicklens.*
import calespiga.config.ElectronicsFanConfig
import calespiga.model.FanSignal
import calespiga.model.FanSignal.TurnOff
import calespiga.model.FanSignal.TurnOn
import calespiga.model.FanSignal.SetAutomatic
import calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured
import calespiga.model.Event.Temperature.GoalTemperatureChanged
import calespiga.model.Event.Temperature.ExternalTemperatureMeasured

private object ElectronicsFanManager {

  val COMMAND_ACTION_SUFFIX = "-command"

  private final case class Impl(config: ElectronicsFanConfig)
      extends SingleProcessor {

    private object Actions {
      private def commandAction(
          command: FanSignal.ControllerState
      ) =
        Action.SendMqttStringMessage(
          config.electronicsFanMqttTopic,
          FanSignal.controllerStateToCommand(command)
        )

      private def periodicCommandAction(
          command: FanSignal.ControllerState
      ) = {
        Action.Periodic(
          config.electronicsFanId + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          config.resendInterval
        )
      }

      def commandActionWithResendElectronics(
          command: FanSignal.ControllerState
      ) = {
        Set(
          commandAction(command),
          periodicCommandAction(command)
        )
      }
    }

    private object Commands {
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

      def commandToSendForElectronics(
          command: FanSignal.UserCommand,
          state: State
      ): FanSignal.ControllerState = command match
        case TurnOff      => FanSignal.Off
        case TurnOn       => FanSignal.On
        case SetAutomatic =>
          automaticCommand(
            state.temperatures.goalTemperature,
            state.temperatures.electronicsTemperature,
            state.temperatures.externalTemperature
          )

    }

    private def processIfSentCommandChangedElectronics(
        state: State,
        commandToSet: FanSignal.ControllerState,
        commandReceived: FanSignal.UserCommand
    ): (State, Set[Action]) = {
      state.fans.fanElectronicsLatestCommandSent match {
        case Some(previousCommandSent) if previousCommandSent == commandToSet =>
          (
            state
              .modify(_.fans.fanElectronicsLatestCommandReceived)
              .setTo(commandReceived),
            Set.empty[Action]
          )
        case _ =>
          (
            state
              .modify(_.fans.fanElectronicsLatestCommandReceived)
              .setTo(commandReceived)
              .modify(_.fans.fanElectronicsLatestCommandSent)
              .setTo(Some(commandToSet)),
            Actions.commandActionWithResendElectronics(commandToSet)
          )
      }
    }

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: java.time.Instant
    ): (State, Set[Action]) = {
      eventData match {
        case Event.System.StartupEvent =>

          (
            state,
            Set(
              Action.SetOpenHabItemValue(
                config.electronicsFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanElectronicsLatestCommandReceived
                )
              )
            ) ++ Actions.commandActionWithResendElectronics(
              Commands.commandToSendForElectronics(
                state.fans.fanElectronicsLatestCommandReceived,
                state
              )
            )
          )

        case ElectronicsFanStatus(status) =>
          val newState = state.modify(_.fans.fanElectronicsStatus).setTo(status)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                config.electronicsFanStatusItem,
                status.toString
              )
            )
          )

        case ElectronicsFanCommand(command) =>
          val commandToSet: FanSignal.ControllerState =
            Commands.commandToSendForElectronics(command, state)
          processIfSentCommandChangedElectronics(state, commandToSet, command)

        case ElectronicsTemperatureMeasured(_) | GoalTemperatureChanged(_) |
            ExternalTemperatureMeasured(_) =>
          if (
              state.fans.fanElectronicsLatestCommandReceived == FanSignal.SetAutomatic
            )
          then
            val commandToSet: FanSignal.ControllerState =
              Commands.commandToSendForElectronics(
                state.fans.fanElectronicsLatestCommandReceived,
                state
              )
            processIfSentCommandChangedElectronics(
              state,
              commandToSet,
              FanSignal.SetAutomatic
            )
          else (state, Set.empty)

        case _ =>
          (state, Set.empty)
      }
    }
  }

  def apply(config: ElectronicsFanConfig): SingleProcessor = Impl(config)

}
