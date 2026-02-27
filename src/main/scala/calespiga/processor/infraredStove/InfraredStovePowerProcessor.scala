package calespiga.processor.infraredStove

import calespiga.model.{State, Action, Event}
import java.time.Instant
import calespiga.config.InfraredStoveConfig
import calespiga.model.Event.InfraredStove.InfraredStovePowerStatusReported
import calespiga.model.Event.InfraredStove.InfraredStovePowerCommandChanged
import calespiga.model.InfraredStoveSignal.TurnOff
import calespiga.model.InfraredStoveSignal.SetAutomatic
import calespiga.model.InfraredStoveSignal.SetPower600
import calespiga.model.InfraredStoveSignal.SetPower1200
import calespiga.model.InfraredStoveSignal
import com.softwaremill.quicklens.*
import calespiga.model.Event.InfraredStove
import java.time.ZoneId
import calespiga.processor.SingleProcessor
import calespiga.processor.utils.EnergyCalculator
import calespiga.processor.utils.CommandActions
import calespiga.processor.utils.ProcessorFormatter

private object InfraredStovePowerProcessor {

  private final case class Impl(
      config: InfraredStoveConfig,
      zone: ZoneId,
      actions: CommandActions[InfraredStoveSignal.ControllerState],
      energyCalculator: EnergyCalculator
  ) extends SingleProcessor {

    private def getDefaultCommandToSend(
        status: InfraredStoveSignal.UserCommand
    ): InfraredStoveSignal.ControllerState = {
      status match
        case TurnOff      => InfraredStoveSignal.Off
        case SetAutomatic => InfraredStoveSignal.Off
        case SetPower600  => InfraredStoveSignal.Power600
        case SetPower1200 => InfraredStoveSignal.Power1200
    }

    override def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = eventData match {

      case isd: Event.InfraredStove.InfraredStoveData =>
        isd match
          case InfraredStovePowerStatusReported(status) =>
            val newEnergyToday = energyCalculator.calculateEnergyToday(
              state.infraredStove.lastChange,
              timestamp,
              state.infraredStove.status.map(_.power).getOrElse(0),
              state.infraredStove.energyToday,
              zone
            )
            val newState = state
              .modify(_.infraredStove.status)
              .setTo(Some(status))
              .modify(_.infraredStove.lastChange)
              .setTo(Some(timestamp))
              .modify(_.infraredStove.energyToday)
              .setTo(newEnergyToday)
              .modify(_.infraredStove.lastTimeConnected)
              .setTo(Some(timestamp))

            val actions: Set[Action] = Set(
              Action.SetUIItemValue(
                config.energyTodayItem,
                newEnergyToday.toInt.toString
              ),
              Action.SetUIItemValue(
                config.statusItem,
                status.power.toString
              ),
              Action.SetUIItemValue(
                config.lastChangeItem,
                ProcessorFormatter.format(timestamp, zone)
              )
            )

            (newState, actions)

          case InfraredStovePowerCommandChanged(status) =>
            val commandToSend = getDefaultCommandToSend(status)

            val newState = state
              .modify(_.infraredStove.lastCommandReceived)
              .setTo(Some(status))
              .modify(_.infraredStove.lastCommandSent)
              .setTo(Some(commandToSend))

            (newState, actions.commandActionWithResend(commandToSend))

          case Event.InfraredStove.InfraredStoveManualTimeExpired =>
            // to protect from a race condition where the manual expires
            // but a command to change to a non manual mode is received, check
            // if the last command received is manual before sending the off command
            state.infraredStove.lastCommandReceived match
              case Some(lastCmd) if Actions.isManualCommand(lastCmd) =>
                val commandToSend = InfraredStoveSignal.Off
                val newState = state
                  .modify(_.infraredStove.lastCommandSent)
                  .setTo(Some(commandToSend))

                (newState, actions.commandActionWithResend(commandToSend))
              case _ =>
                (state, Set.empty)

          case _ => (state, Set.empty)

      case Event.System.StartupEvent =>
        val commandToSend = getDefaultCommandToSend(
          state.infraredStove.lastCommandReceived.getOrElse(TurnOff)
        )
        val newState = state
          .modify(_.infraredStove.lastCommandSent)
          .setTo(Some(commandToSend))
          .modify(_.infraredStove.lastChange)
          .setTo(Some(timestamp))

        (
          newState,
          actions.commandActionWithResend(commandToSend) +
            Action.SetUIItemValue(
              config.lastCommandItem,
              InfraredStoveSignal.userCommandToString(
                state.infraredStove.lastCommandReceived.getOrElse(TurnOff)
              )
            )
        )

      case _ =>
        (state, Set.empty)
    }

  }

  def apply(
      config: InfraredStoveConfig,
      zone: ZoneId
  ): SingleProcessor = Impl(
    config,
    zone,
    Actions(config),
    EnergyCalculator()
  )

  // Overloaded apply for testing with injected dependencies
  private[infraredStove] def apply(
      config: InfraredStoveConfig,
      zone: ZoneId,
      energyCalculator: EnergyCalculator
  ): SingleProcessor = Impl(
    config,
    zone,
    Actions(config),
    energyCalculator
  )
}
