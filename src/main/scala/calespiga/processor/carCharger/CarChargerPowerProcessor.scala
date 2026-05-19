package calespiga.processor.carCharger

import calespiga.config.CarChargerConfig
import calespiga.model.{State, Action, Event}
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.CommandActions
import com.softwaremill.quicklens.*
import java.time.Instant

private[carCharger] object CarChargerPowerProcessor {

  private final case class Impl(config: CarChargerConfig)
      extends SingleProcessor {

    private val actions =
      CommandActions[calespiga.model.CarChargerSignal.ControllerState](
        config.mqttTopicForCommand,
        config.id,
        config.resendInterval,
        calespiga.model.CarChargerSignal.controllerStateToString
      )

    private def getDefaultCommandToSend(
        status: calespiga.model.CarChargerSignal.UserCommand
    ): calespiga.model.CarChargerSignal.ControllerState =
      status match
        case calespiga.model.CarChargerSignal.TurnOff =>
          calespiga.model.CarChargerSignal.Off
        case calespiga.model.CarChargerSignal.TurnOn =>
          calespiga.model.CarChargerSignal.On
        case calespiga.model.CarChargerSignal.SetAutomatic =>
          calespiga.model.CarChargerSignal.Off

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) =
      eventData match

        case Event.CarCharger.CarChargerPowerCommandChanged(command) =>
          val commandToSend = getDefaultCommandToSend(command)

          val newState = state
            .modify(_.carCharger.lastCommandReceived)
            .setTo(Some(command))
            .modify(_.carCharger.lastCommandSent)
            .setTo(Some(commandToSend))
            .modify(_.carCharger.lastChange)
            .setTo(Some(timestamp))

          (
            newState,
            actions.commandActionWithResend(commandToSend)
          )

        case Event.System.StartupEvent =>
          val commandToSend = getDefaultCommandToSend(
            state.carCharger.lastCommandReceived.getOrElse(
              calespiga.model.CarChargerSignal.TurnOff
            )
          )

          val newState = state
            .modify(_.carCharger.lastCommandSent)
            .setTo(Some(commandToSend))
            .modify(_.carCharger.lastChange)
            .setTo(Some(timestamp))

          (
            newState,
            actions.commandActionWithResend(commandToSend) ++ Set(
              Action.SetUIItemValue(
                config.lastCommandItem,
                calespiga.model.CarChargerSignal.userCommandToString(
                  state.carCharger.lastCommandReceived.getOrElse(
                    calespiga.model.CarChargerSignal.TurnOff
                  )
                )
              )
            )
          )

        case _ =>
          (state, Set.empty)
  }

  def apply(config: CarChargerConfig): SingleProcessor = Impl(config)

}
