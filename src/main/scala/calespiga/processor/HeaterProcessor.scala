package calespiga.processor

import calespiga.model.{State, Action, Event, Switch}
import java.time.Instant
import calespiga.model.Event.Heater.*
import com.softwaremill.quicklens.*
import calespiga.config.HeaterConfig
import scala.concurrent.duration._
import calespiga.model.RemoteHeaterPowerState.RemoteHeaterPowerStatus
import calespiga.model.RemoteHeaterPowerState
import calespiga.processor.utils.RemoteStateActionManager

object HeaterProcessor {

  private val id = "heater-processor"
  private val resendInterval = 20.seconds
  private val timeoutInterval = 1.minutes

  private final case class Impl(
      manager: RemoteStateActionManager[RemoteHeaterPowerStatus]
  ) extends StateProcessor.SingleProcessor {

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case hd: Event.Heater.HeaterData =>
        hd match
          case HeaterPowerStatusReported(status) =>
            // TODO update any inconsistency timers, calculate spent energy.
            (state, Set.empty)

          case HeaterPowerCommandChanged(status) =>
            state.heater.heaterManagementAutomatic match {
              case Switch.On =>
                // in automatic mode, store user commands but not apply them
                val newState = state
                  .modify(_.heater.lastCommandReceived)
                  .setTo(Some(status))
                (newState, Set.empty)
              case Switch.Off =>
                // in manual mode, apply user command and update last command
                val (actions, newRemoteState) =
                  manager.turnRemote(
                    status,
                    state.heater.status
                  )
                val newState = state
                  .modify(_.heater.status)
                  .setTo(newRemoteState)
                  .modify(_.heater.lastCommandReceived)
                  .setTo(Some(status))
                (newState, actions)
            }

          case HeaterIsHotReported(Switch.On) =>
            // heater is HOT, turn it off, mark it as hot and update last time hot
            val (actions, newRemoteState) =
              manager.turnRemote(
                RemoteHeaterPowerStatus.Off,
                state.heater.status
              )
            val newState = state
              .modify(_.heater.isHot)
              .setTo(Switch.On)
              .modify(_.heater.lastTimeHot)
              .setTo(Some(timestamp))
              .modify(_.heater.status)
              .setTo(newRemoteState)
            (newState, actions)

          case HeaterIsHotReported(Switch.Off) =>
            // heater is COLD, mark it as not hot
            // if the management is manual, we should propagate the last user command. Otherwise we should turn it off
            val commandToSet = state.heater.heaterManagementAutomatic match {
              case Switch.Off =>
                state.heater.lastCommandReceived.getOrElse(
                  RemoteHeaterPowerStatus.Off
                )
              case Switch.On =>
                RemoteHeaterPowerStatus.Off
            }

            val (actions, newRemoteState) =
              manager.turnRemote(
                commandToSet,
                state.heater.status
              )
            val newState = state
              .modify(_.heater.isHot)
              .setTo(Switch.Off)
              .modify(_.heater.status)
              .setTo(newRemoteState)
            (newState, actions)

          case HeaterManagementAutomaticChanged(Switch.On) =>
            // the heater goes automatic. Turn it off and the automatic process will manage it
            val (actions, newRemoteState) =
              manager.turnRemote(
                RemoteHeaterPowerStatus.Off,
                state.heater.status
              )
            val newState = state
              .modify(_.heater.heaterManagementAutomatic)
              .setTo(Switch.On)
              .modify(_.heater.status)
              .setTo(newRemoteState)
            (newState, actions)

          case HeaterManagementAutomaticChanged(Switch.Off) =>
            // the heater goes back to manual. Change state and apply last command received
            val (actions, newRemoteState) =
              manager.turnRemote(
                state.heater.lastCommandReceived.getOrElse(
                  RemoteHeaterPowerStatus.Off
                ),
                state.heater.status
              )
            val newState = state
              .modify(_.heater.heaterManagementAutomatic)
              .setTo(Switch.Off)
              .modify(_.heater.status)
              .setTo(newRemoteState)
            (newState, actions)
      case _ =>
        (state, Set.empty)
    }

  }

  def apply(config: HeaterConfig): StateProcessor.SingleProcessor = Impl(
    RemoteStateActionManager(
      id = id,
      resendInterval = resendInterval,
      timeoutInterval = timeoutInterval,
      mqttTopicForCommand = config.mqttTopicForCommand,
      inconsistencyUIItem = config.inconsistencyUIItem
    )
  )

  def apply(
      remoteManager: RemoteStateActionManager[RemoteHeaterPowerStatus]
  ): StateProcessor.SingleProcessor = Impl(
    remoteManager
  )
}
