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
import calespiga.model.Event.Heater
import java.time.ZoneId
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.EnergyCalculator
import calespiga.processor.utils.CommandActions
import calespiga.processor.utils.ProcessorFormatter

private object HeaterPowerProcessor {

  private final case class Impl(
      config: HeaterConfig,
      zone: ZoneId,
      actions: CommandActions[HeaterSignal.ControllerState],
      energyCalculator: EnergyCalculator
  ) extends SingleProcessor {

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
            val newEnergyToday = energyCalculator.calculateEnergyToday(
              state.heater.lastChange,
              timestamp,
              state.heater.status.map(_.power).getOrElse(0),
              state.heater.energyToday,
              zone
            )
            val newState = state
              .modify(_.heater.status)
              .setTo(Some(status))
              .modify(_.heater.lastChange)
              .setTo(Some(timestamp))
              .modify(_.heater.energyToday)
              .setTo(newEnergyToday)

            val actions: Set[Action] = Set(
              Action.SetUIItemValue(
                config.energyTodayItem,
                newEnergyToday.toInt.toString
              ),
              Action.SetUIItemValue(
                config.statusItem,
                status.power.toString
              )
            )

            (newState, actions)

          case HeaterPowerCommandChanged(status) =>
            val commandToSend = getDefaultCommandToSend(status)

            val newState = state
              .modify(_.heater.lastCommandReceived)
              .setTo(Some(status))
              .modify(_.heater.lastCommandSent)
              .setTo(Some(commandToSend))

            (newState, actions.commandActionWithResend(commandToSend))

          case HeaterIsHotReported(status) =>
            if (status != state.heater.isHot) {
              status match
                case HeaterSignal.Hot =>
                  val commandToSend = HeaterSignal.Off
                  val newState = state
                    .modify(_.heater.isHot)
                    .setTo(HeaterSignal.Hot)
                    .modify(_.heater.lastTimeHot)
                    .setTo(Some(timestamp))
                    .modify(_.heater.lastCommandSent)
                    .setTo(Some(commandToSend))

                  (
                    newState,
                    actions.commandActionWithResend(commandToSend) + Action
                      .SetUIItemValue(
                        config.lastTimeHotItem,
                        ProcessorFormatter.format(timestamp, zone)
                      ) + Action.SetUIItemValue(
                      config.isHotItem,
                      HeaterSignal.Hot.toString
                    )
                  )
                case HeaterSignal.Cold =>
                  val commandToSend = getDefaultCommandToSend(
                    state.heater.lastCommandReceived.getOrElse(TurnOff)
                  )
                  val newState = state
                    .modify(_.heater.isHot)
                    .setTo(HeaterSignal.Cold)
                    .modify(_.heater.lastTimeHot)
                    .setTo(Some(timestamp))
                    .modify(_.heater.lastCommandSent)
                    .setTo(Some(commandToSend))

                  (
                    newState,
                    actions.commandActionWithResend(commandToSend) + Action
                      .SetUIItemValue(
                        config.lastTimeHotItem,
                        ProcessorFormatter.format(timestamp, zone)
                      ) + Action
                      .SetUIItemValue(
                        config.isHotItem,
                        HeaterSignal.Cold.toString
                      )
                  )
            } else {
              (state, Set.empty)
            }

      case Event.System.StartupEvent =>
        val commandToSend = getDefaultCommandToSend(
          state.heater.lastCommandReceived.getOrElse(TurnOff)
        )
        val newState = state
          .modify(_.heater.lastCommandSent)
          .setTo(Some(commandToSend))
          .modify(_.heater.lastChange)
          .setTo(Some(timestamp))

        (
          newState,
          actions.commandActionWithResend(commandToSend) +
            Action.SetUIItemValue(
              config.lastCommandItem,
              HeaterSignal.userCommandToString(
                state.heater.lastCommandReceived.getOrElse(TurnOff)
              )
            )
        )

      case _ =>
        (state, Set.empty)
    }

  }

  def apply(
      config: HeaterConfig,
      zone: ZoneId
  ): SingleProcessor = Impl(
    config,
    zone,
    Actions(config),
    EnergyCalculator()
  )

  // Overloaded apply for testing with injected dependencies
  private[heater] def apply(
      config: HeaterConfig,
      zone: ZoneId,
      energyCalculator: EnergyCalculator
  ): SingleProcessor = Impl(
    config,
    zone,
    Actions(config),
    energyCalculator
  )
}
