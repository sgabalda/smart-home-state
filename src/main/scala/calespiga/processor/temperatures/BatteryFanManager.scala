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
import calespiga.model.Event.Temperature.{
  BatteryClosetTemperatureMeasured,
  ExternalTemperatureMeasured
}
import calespiga.model.Event.Temperature.GoalTemperatureChanged
import calespiga.processor.temperatures.utils.FanCommandsCreator

private object BatteryFanManager {

  private final case class Impl(config: BatteryFanConfig)
      extends SingleProcessor {

    private val commands =
      FanCommandsCreator(
        state => state.temperatures.batteriesClosetTemperature,
        config.batteryFanId,
        config.resendInterval,
        config.batteryFanMqttTopic
      )

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
            commands.commandActionWithResend(commandToSet)
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
              Action.SetUIItemValue(
                config.batteryFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanBatteriesLatestCommandReceived
                )
              )
            ) ++ commands.commandActionWithResend(
              commands.commandToSend(
                state.fans.fanBatteriesLatestCommandReceived,
                state
              )
            )
          )

        case BatteryFanStatus(status) =>
          val newState = state.modify(_.fans.fanBatteriesStatus).setTo(status)
          (
            newState,
            Set(
              Action.SetUIItemValue(
                config.batteryFanStatusItem,
                status.toString
              )
            )
          )

        case BatteryFanCommand(command) =>
          val commandToSet: FanSignal.ControllerState =
            commands.commandToSend(command, state)
          processIfSentCommandChangedBattery(state, commandToSet, command)

        case BatteryClosetTemperatureMeasured(_) | GoalTemperatureChanged(_) |
            ExternalTemperatureMeasured(_) =>
          if (
              state.fans.fanBatteriesLatestCommandReceived == FanSignal.SetAutomatic
            )
          then
            val commandToSet: FanSignal.ControllerState =
              commands.commandToSend(
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
