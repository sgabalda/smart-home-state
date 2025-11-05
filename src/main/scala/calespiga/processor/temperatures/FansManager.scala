package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.model.Event
import calespiga.model.Event.Temperature.Fans.BatteryFanStatus
import calespiga.model.Event.Temperature.Fans.ElectronicsFanStatus
import calespiga.model.Event.Temperature.Fans.BatteryFanCommand
import calespiga.model.Event.Temperature.Fans.ElectronicsFanCommand
import calespiga.model.State
import calespiga.model.Action
import com.softwaremill.quicklens.*
import calespiga.config.FansConfig
import calespiga.model.FanSignal
import calespiga.model.FanSignal.TurnOff
import calespiga.model.FanSignal.TurnOn
import calespiga.model.FanSignal.SetAutomatic
import calespiga.model.Event.Temperature.BatteryClosetTemperatureMeasured
import calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured
import calespiga.model.Event.Temperature.GoalTemperatureChanged

private object FansManager {

  val COMMAND_ACTION_SUFFIX = "-command"

  private final case class Impl(config: FansConfig) extends SingleProcessor {

    private object Actions {
      private def commandAction(
          command: FanSignal.ControllerState,
          topic: String
      ) =
        Action.SendMqttStringMessage(
          topic,
          FanSignal.controllerStateToCommand(command)
        )

      private def periodicCommandAction(
          command: FanSignal.ControllerState,
          topic: String,
          id: String
      ) = {
        Action.Periodic(
          id + COMMAND_ACTION_SUFFIX,
          commandAction(command, topic),
          config.resendInterval
        )
      }

      def commandActionWithResendBattery(command: FanSignal.ControllerState) = {
        Set(
          commandAction(command, config.batteryFanMqttTopic),
          periodicCommandAction(
            command,
            config.batteryFanMqttTopic,
            config.batteryFanId
          )
        )
      }

      def commandActionWithResendElectronics(
          command: FanSignal.ControllerState
      ) = {
        Set(
          commandAction(command, config.electronicsFanMqttTopic),
          periodicCommandAction(
            command,
            config.electronicsFanMqttTopic,
            config.electronicsFanId
          )
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

      def commandToSendForBattery(
          command: FanSignal.UserCommand,
          state: State
      ): FanSignal.ControllerState = command match
        case TurnOff      => FanSignal.Off
        case TurnOn       => FanSignal.On
        case SetAutomatic =>
          automaticCommand(
            state.temperatures.goalTemperature,
            state.temperatures.batteriesClosetTemperature,
            state.temperatures.externalTemperature
          )

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
                config.batteryFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanBatteriesLatestCommand
                )
              ),
              Action.SetOpenHabItemValue(
                config.electronicsFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanElectronicsLatestCommand
                )
              )
            ) ++ Actions.commandActionWithResendBattery(
              Commands.commandToSendForBattery(
                state.fans.fanBatteriesLatestCommand,
                state
              )
            ) ++ Actions.commandActionWithResendElectronics(
              Commands.commandToSendForElectronics(
                state.fans.fanElectronicsLatestCommand,
                state
              )
            )
          )

        case BatteryFanStatus(status) =>
          val newState = state.modify(_.fans.fanBatteries).setTo(status)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                config.batteryFanStatusItem,
                status.toString
              )
            )
          )

        case ElectronicsFanStatus(status) =>
          val newState = state.modify(_.fans.fanElectronics).setTo(status)
          (
            newState,
            Set(
              Action.SetOpenHabItemValue(
                config.electronicsFanStatusItem,
                status.toString
              )
            )
          )

        case BatteryFanCommand(command) =>
          val commandToSet: FanSignal.ControllerState =
            Commands.commandToSendForBattery(command, state)
          val previousCommand = state.fans.fanBatteriesLatestCommand
          val actions = if (previousCommand != command) {
            Actions.commandActionWithResendBattery(commandToSet)
          } else Set.empty[Action]

          val newState =
            state.modify(_.fans.fanBatteriesLatestCommand).setTo(command)
          (newState, actions)

        case ElectronicsFanCommand(command) =>
          val commandToSet: FanSignal.ControllerState =
            Commands.commandToSendForElectronics(command, state)
          val previousCommand = state.fans.fanElectronicsLatestCommand
          val actions = if (previousCommand != command) {
            Actions.commandActionWithResendElectronics(commandToSet)
          } else Set.empty[Action]

          val newState =
            state.modify(_.fans.fanElectronicsLatestCommand).setTo(command)
          (newState, actions)

        case BatteryClosetTemperatureMeasured(_) =>
          if (state.fans.fanBatteriesLatestCommand == FanSignal.SetAutomatic)
          then
            val commandToSet: FanSignal.ControllerState =
              Commands.commandToSendForBattery(
                state.fans.fanBatteriesLatestCommand,
                state
              )
            val actions = Actions.commandActionWithResendBattery(commandToSet)
            (state, actions)
          else (state, Set.empty)

        case ElectronicsTemperatureMeasured(_) =>
          if (state.fans.fanElectronicsLatestCommand == FanSignal.SetAutomatic)
          then
            val commandToSet: FanSignal.ControllerState =
              Commands.commandToSendForElectronics(
                state.fans.fanElectronicsLatestCommand,
                state
              )
            val actions =
              Actions.commandActionWithResendElectronics(commandToSet)
            (state, actions)
          else (state, Set.empty)

        case GoalTemperatureChanged(_) =>
          val batteryActions =
            if (state.fans.fanBatteriesLatestCommand == FanSignal.SetAutomatic)
            then
              val commandToSet: FanSignal.ControllerState =
                Commands.commandToSendForBattery(
                  state.fans.fanBatteriesLatestCommand,
                  state
                )
              Actions.commandActionWithResendBattery(commandToSet)
            else Set.empty[Action]

          val electronicsActions =
            if (
                state.fans.fanElectronicsLatestCommand == FanSignal.SetAutomatic
              )
            then
              val commandToSet: FanSignal.ControllerState =
                Commands.commandToSendForElectronics(
                  state.fans.fanElectronicsLatestCommand,
                  state
                )
              Actions.commandActionWithResendElectronics(commandToSet)
            else Set.empty[Action]

          (state, batteryActions ++ electronicsActions)

        case _ =>
          (state, Set.empty)
      }
    }
  }

  def apply(config: FansConfig): SingleProcessor = Impl(config)

}
