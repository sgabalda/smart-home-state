package calespiga.processor.heater

import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.config.HeaterConfig
import calespiga.model.Event.Heater.HeaterPowerStatusReported
import calespiga.model.Event.Heater.HeaterPowerCommandChanged
import calespiga.model.Event.Heater.HeaterIsHotReported
import calespiga.model.HeaterSignal.TurnOff
import calespiga.model.HeaterSignal.SetAutomatic
import calespiga.model.HeaterSignal.SetPower500
import calespiga.model.HeaterSignal.SetPower1000
import calespiga.model.HeaterSignal.SetPower2000
import calespiga.model.HeaterSignal
import com.softwaremill.quicklens.*
import calespiga.model.Switch.On
import calespiga.model.Switch.Off
import calespiga.model.Event.Heater
import java.time.ZoneId
import calespiga.processor.StateProcessor

object HeaterProcessor {

  val COMMAND_ACTION_SUFFIX = "-command"

  private final case class Impl(config: HeaterConfig, zone: ZoneId)
      extends StateProcessor.SingleProcessor {

    private object Actions {
      private def commandAction(command: HeaterSignal.ControllerState) =
        Action.SendMqttStringMessage(
          config.mqttTopicForCommand,
          command.toString
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
      def commandActionWithResend(command: HeaterSignal.ControllerState) = {
        Set(commandAction(command), periodicCommandAction(command))
      }
    }

    private def getDefaultCommandToSend(
        status: HeaterSignal.UserCommand
    ): HeaterSignal.ControllerState = {
      status match
        case TurnOff      => HeaterSignal.Off
        case SetAutomatic => HeaterSignal.Off
        case SetPower500  => HeaterSignal.Power500
        case SetPower1000 => HeaterSignal.Power1000
        case SetPower2000 => HeaterSignal.Power2000
    }
    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case hd: Event.Heater.HeaterData =>
        hd match
          case HeaterPowerStatusReported(status) =>
            val lastEnergyUpdate = state.heater.lastChange.getOrElse(timestamp)
            val sameDay = lastEnergyUpdate.atZone(zone).toLocalDate == timestamp
              .atZone(zone)
              .toLocalDate
            val energyLastPeriod =
              lastEnergyUpdate.until(timestamp).toMillis * state.heater.status
                .map(_.power)
                .getOrElse(0) / 1000f / 3600f
            val newEnergyToday =
              if (sameDay) state.heater.energyToday + energyLastPeriod
              else energyLastPeriod
            val newState = state
              .modify(_.heater.status)
              .setTo(Some(status))
              .modify(_.heater.lastChange)
              .setTo(Some(timestamp))
              .modify(_.heater.energyToday)
              .setTo(newEnergyToday)

            val actions: Set[Action] = Set(
              Action.SetOpenHabItemValue(
                config.energyTodayItem,
                newEnergyToday.toString
              ),
              Action.SetOpenHabItemValue(config.statusItem, status.toString)
            )

            (newState, actions)

          case HeaterPowerCommandChanged(status) =>
            val commandToSend = getDefaultCommandToSend(status)
            val newState = state
              .modify(_.heater.lastCommandReceived)
              .setTo(Some(status))
              .modify(_.heater.lastCommandSent)
              .setTo(Some(commandToSend))

            (newState, Actions.commandActionWithResend(commandToSend))

          case HeaterIsHotReported(status) =>
            status match
              case On =>
                val commandToSend = HeaterSignal.Off
                val newState = state
                  .modify(_.heater.isHot)
                  .setTo(On)
                  .modify(_.heater.lastTimeHot)
                  .setTo(Some(timestamp))
                  .modify(_.heater.lastCommandSent)
                  .setTo(Some(commandToSend))

                (
                  newState,
                  Actions.commandActionWithResend(commandToSend) + Action
                    .SetOpenHabItemValue(
                      config.lastTimeHotItem,
                      timestamp.toString
                    )
                )
              case Off =>
                val commandToSend = getDefaultCommandToSend(
                  state.heater.lastCommandReceived.getOrElse(TurnOff)
                )
                val newState = state
                  .modify(_.heater.isHot)
                  .setTo(Off)
                  .modify(_.heater.lastCommandSent)
                  .setTo(Some(commandToSend))

                (newState, Actions.commandActionWithResend(commandToSend))

      case Event.System.StartupEvent =>
        val commandToSend = getDefaultCommandToSend(
          state.heater.lastCommandReceived.getOrElse(TurnOff)
        )
        val newState = state
          .modify(_.heater.lastCommandSent)
          .setTo(Some(commandToSend))
          .modify(_.heater.lastChange)
          .setTo(Some(timestamp))

        (newState, Actions.commandActionWithResend(commandToSend))

      case _ =>
        (state, Set.empty)
    }

  }

  def apply(
      config: HeaterConfig,
      zone: ZoneId
  ): StateProcessor.SingleProcessor = Impl(
    config,
    zone
  )
}
