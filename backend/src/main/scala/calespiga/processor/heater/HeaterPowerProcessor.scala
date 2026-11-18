package calespiga.processor.heater

import calespiga.config.HeaterConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.Heater
import calespiga.model.Event.Heater.HeaterGoalTemperatureChanged
import calespiga.model.Event.Heater.HeaterPowerCommandChanged
import calespiga.model.Event.Heater.HeaterPowerStatusReported
import calespiga.model.Event.Heater.HeaterTemperatureMeasured
import calespiga.model.HeaterSignal
import calespiga.model.HeaterSignal.SetAutomatic
import calespiga.model.HeaterSignal.SetPower1000
import calespiga.model.HeaterSignal.SetPower2000
import calespiga.model.HeaterSignal.SetPower500
import calespiga.model.HeaterSignal.TurnOff
import calespiga.model.State
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.CommandActions
import calespiga.processor.utils.EnergyCalculator
import calespiga.processor.utils.ProcessorFormatter
import com.softwaremill.quicklens.*
import java.time.Instant
import java.time.ZoneId

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

    private def temperatureState(
        state: State,
        timestamp: Instant,
        updateStatusItem: Boolean
    ): (State, Set[Action]) = {
      state.heater.temperature match {
        case None =>
          (state, Set.empty)
        case Some(temperature) =>
          val newStatus =
            if (temperature >= state.heater.goalTemperature) HeaterSignal.Hot
            else HeaterSignal.Cold
          val statusChanged = newStatus != state.heater.isHot
          if (!statusChanged && !updateStatusItem) {
            (state, Set.empty)
          } else {
            val commandToSend =
              if (newStatus == HeaterSignal.Hot) HeaterSignal.Off
              else
                getDefaultCommandToSend(
                  state.heater.lastCommandReceived.getOrElse(TurnOff)
                )
            val newState = state
              .modify(_.heater.isHot)
              .setTo(newStatus)
              .modify(_.heater.lastCommandSent)
              .setTo(Some(commandToSend))
            val thermostatActions =
              if (statusChanged) actions.commandActionWithResend(commandToSend)
              else Set.empty[Action]
            val statusActions =
              if (statusChanged || updateStatusItem) {
                Set(
                  Action.SetUIItemValue(config.isHotItem, newStatus.toString)
                )
              } else Set.empty[Action]
            val hotActions =
              if (statusChanged && newStatus == HeaterSignal.Hot) {
                val hotState = newState
                  .modify(_.heater.lastTimeHot)
                  .setTo(Some(timestamp))
                val hotTimestampAction = Action.SetUIItemValue(
                  config.lastTimeHotItem,
                  ProcessorFormatter.format(timestamp, zone)
                )
                (hotState, Set[Action](hotTimestampAction))
              } else {
                (newState, Set.empty[Action])
              }

            (
              hotActions._1,
              thermostatActions ++ statusActions ++ hotActions._2
            )
          }
      }
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
            val commandToSend =
              if (state.heater.isHot == HeaterSignal.Hot) HeaterSignal.Off
              else getDefaultCommandToSend(status)

            val newState = state
              .modify(_.heater.lastCommandReceived)
              .setTo(Some(status))
              .modify(_.heater.lastCommandSent)
              .setTo(Some(commandToSend))

            (newState, actions.commandActionWithResend(commandToSend))

          case HeaterTemperatureMeasured(celsius) if celsius == -127.0 =>
            (
              state,
              Set(
                Action.SetUIItemValue(
                  config.temperatureItem,
                  celsius.toString
                )
              )
            )

          case HeaterTemperatureMeasured(celsius) =>
            val firstReading = state.heater.temperature.isEmpty
            val updatedState = state
              .modify(_.heater.temperature)
              .setTo(Some(celsius))
            val (thermostatState, thermostatActions) =
              temperatureState(updatedState, timestamp, firstReading)
            (
              thermostatState,
              thermostatActions + Action.SetUIItemValue(
                config.temperatureItem,
                celsius.toString
              )
            )

          case HeaterGoalTemperatureChanged(celsius) =>
            val updatedState = state
              .modify(_.heater.goalTemperature)
              .setTo(celsius)
            val (thermostatState, thermostatActions) =
              temperatureState(updatedState, timestamp, false)
            (
              thermostatState,
              thermostatActions + Action.SetUIItemValue(
                config.goalTemperatureItem,
                celsius.toString
              )
            )

      case Event.System.StartupEvent =>
        val (thermostatState, thermostatActions) =
          temperatureState(state, timestamp, true)
        val commandToSend =
          if (thermostatState.heater.isHot == HeaterSignal.Hot)
            HeaterSignal.Off
          else
            getDefaultCommandToSend(
              thermostatState.heater.lastCommandReceived.getOrElse(TurnOff)
            )
        val newState = thermostatState
          .modify(_.heater.lastCommandSent)
          .setTo(Some(commandToSend))
          .modify(_.heater.lastChange)
          .setTo(Some(timestamp))

        (
          newState,
          actions.commandActionWithResend(commandToSend) ++
            thermostatActions + Action.SetUIItemValue(
              config.lastCommandItem,
              HeaterSignal.userCommandToString(
                thermostatState.heater.lastCommandReceived.getOrElse(TurnOff)
              )
            ) + Action.SetUIItemValue(
              config.goalTemperatureItem,
              thermostatState.heater.goalTemperature.toString
            ) ++ thermostatState.heater.temperature
              .map(temperature =>
                Set[Action](
                  Action.SetUIItemValue(
                    config.temperatureItem,
                    temperature.toString
                  )
                )
              )
              .getOrElse(Set.empty)
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
