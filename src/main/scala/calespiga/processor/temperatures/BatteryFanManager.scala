package calespiga.processor.temperatures

import calespiga.processor.SingleProcessor
import calespiga.model.Event
import calespiga.model.Event.Temperature.Fans.BatteryFanStatus
import calespiga.model.Event.Temperature.Fans.BatteryFanCommand
import calespiga.model.State
import calespiga.model.Action
import com.softwaremill.quicklens.*
import calespiga.config.BatteryFanConfig
import calespiga.model.FanSignal
import calespiga.model.FanSignal.TurnOff
import calespiga.model.FanSignal.TurnOn
import calespiga.model.FanSignal.SetAutomatic
import calespiga.model.Event.Temperature.{
  BatteryClosetTemperatureMeasured,
  ExternalTemperatureMeasured
}
import calespiga.model.Event.Temperature.GoalTemperatureChanged

private object BatteryFanManager {

  val COMMAND_ACTION_SUFFIX = "-command"

  private final case class Impl(config: BatteryFanConfig)
      extends SingleProcessor {

    private object Actions {
      private def commandAction(command: FanSignal.ControllerState) =
        Action.SendMqttStringMessage(
          config.batteryFanMqttTopic,
          FanSignal.controllerStateToCommand(command)
        )

      private def periodicCommandAction(
          command: FanSignal.ControllerState
      ) = {
        Action.Periodic(
          config.batteryFanId + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          config.resendInterval
        )
      }

      def commandActionWithResendBattery(command: FanSignal.ControllerState) = {
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

    }

    private def processIfSentCommandChangedBattery(
        state: State,
        commandToSet: FanSignal.ControllerState,
        commandReceived: FanSignal.UserCommand
    ): (State, Set[Action]) = {
      state.fans.fanBatteriesLatestCommandSent match {
        case Some(previousCommandSent) if previousCommandSent == commandToSet =>
          (
            state
              .modify(_.fans.fanBatteriesLatestCommandReceived)
              .setTo(commandReceived),
            Set.empty[Action]
          )
        case _ =>
          (
            state
              .modify(_.fans.fanBatteriesLatestCommandReceived)
              .setTo(commandReceived)
              .modify(_.fans.fanBatteriesLatestCommandSent)
              .setTo(Some(commandToSet)),
            Actions.commandActionWithResendBattery(commandToSet)
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
                config.batteryFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanBatteriesLatestCommandReceived
                )
              )
            ) ++ Actions.commandActionWithResendBattery(
              Commands.commandToSendForBattery(
                state.fans.fanBatteriesLatestCommandReceived,
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

        case BatteryFanCommand(command) =>
          val commandToSet: FanSignal.ControllerState =
            Commands.commandToSendForBattery(command, state)
          processIfSentCommandChangedBattery(state, commandToSet, command)

        case BatteryClosetTemperatureMeasured(_) | GoalTemperatureChanged(_) |
            ExternalTemperatureMeasured(_) =>
          if (
              state.fans.fanBatteriesLatestCommandReceived == FanSignal.SetAutomatic
            )
          then
            val commandToSet: FanSignal.ControllerState =
              Commands.commandToSendForBattery(
                state.fans.fanBatteriesLatestCommandReceived,
                state
              )
            processIfSentCommandChangedBattery(
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

  def apply(config: BatteryFanConfig): SingleProcessor = Impl(config)

}
