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
import calespiga.model.Event.Temperature.ElectronicsTemperatureMeasured
import calespiga.model.Event.Temperature.GoalTemperatureChanged
import calespiga.model.Event.Temperature.ExternalTemperatureMeasured
import calespiga.processor.temperatures.utils.FanCommandsCreator

private object ElectronicsFanManager {

  private final case class Impl(config: ElectronicsFanConfig)
      extends SingleProcessor {

    private val commands =
      FanCommandsCreator(
        state => state.temperatures.electronicsTemperature,
        config.electronicsFanId,
        config.resendInterval,
        config.electronicsFanMqttTopic
      )

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
              Action.SetOpenHabItemValue(
                config.electronicsFanCommandItem,
                FanSignal.userCommandToString(
                  state.fans.fanElectronicsLatestCommandReceived
                )
              )
            ) ++ commands.commandActionWithResend(
              commands.commandToSend(
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
            commands.commandToSend(command, state)
          processIfSentCommandChangedElectronics(state, commandToSet, command)

        case ElectronicsTemperatureMeasured(_) | GoalTemperatureChanged(_) |
            ExternalTemperatureMeasured(_) =>
          if (
              state.fans.fanElectronicsLatestCommandReceived == FanSignal.SetAutomatic
            )
          then
            val commandToSet: FanSignal.ControllerState =
              commands.commandToSend(
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
