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
import java.time.format.DateTimeFormatter

private object InfraredStovePowerProcessor {

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  private final case class Impl(
      config: InfraredStoveConfig,
      zone: ZoneId,
      actions: Actions
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
            val lastEnergyUpdate =
              state.infraredStove.lastChange.getOrElse(timestamp)
            val sameDay = lastEnergyUpdate.atZone(zone).toLocalDate == timestamp
              .atZone(zone)
              .toLocalDate
            val energyLastPeriod =
              lastEnergyUpdate
                .until(timestamp)
                .toMillis * state.infraredStove.status
                .map(_.power)
                .getOrElse(0) / 1000f / 3600f
            val newEnergyToday =
              if (sameDay) state.infraredStove.energyToday + energyLastPeriod
              else energyLastPeriod
            val newState = state
              .modify(_.infraredStove.status)
              .setTo(Some(status))
              .modify(_.infraredStove.lastChange)
              .setTo(Some(timestamp))
              .modify(_.infraredStove.energyToday)
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

          case InfraredStovePowerCommandChanged(status) =>
            val commandToSend = getDefaultCommandToSend(status)

            val newState = state
              .modify(_.infraredStove.lastCommandReceived)
              .setTo(Some(status))
              .modify(_.infraredStove.lastCommandSent)
              .setTo(Some(commandToSend))

            (newState, actions.commandActionWithResend(commandToSend))

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
    Actions(config)
  )
}
