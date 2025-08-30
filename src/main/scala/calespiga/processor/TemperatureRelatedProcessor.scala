package calespiga.processor

import calespiga.model.{Action, Event, State}
import com.softwaremill.quicklens.*
import calespiga.model.RemoteState
import calespiga.model.RemoteSwitch.*
import java.time.Instant
import calespiga.processor.RemoteStateProcessor.*
import calespiga.processor.RemoteStateActionProducer.*
import calespiga.model.Switch

object TemperatureRelatedProcessor {

  private final case class Impl(
      batteryFanActionProducer: RemoteSwitchActionProducer,
      electronicsFanActionProducer: RemoteSwitchActionProducer,
      config: calespiga.config.TemperatureRelatedConfig
  ) extends StateProcessor.SingleProcessor {

    def process(
        state: State,
        eventData: Event.EventData,
        timestamp: Instant
    ): (State, Set[Action]) = {
      eventData match {
        case Event.Temperature.BatteryTemperatureMeasured(temperature)
            if temperature != state.temperatures.batteriesTemperature =>
          val newState =
            state.modify(_.temperatures.batteriesTemperature).setTo(temperature)
          val tempActions = Set(
            Action.SetOpenHabItemValue(
              config.batteryTemperatureItem,
              temperature.toString
            )
          )

          // If automatic mode is enabled, check if battery fan should be controlled
          val automaticActions =
            if (state.fans.fanManagementAutomatic == Switch.On) {
              automaticBatteryFanControl(newState, timestamp)
            } else Set.empty

          (newState, tempActions ++ automaticActions)
        case Event.Temperature.BatteryClosetTemperatureMeasured(temperature)
            if temperature != state.temperatures.batteriesClosetTemperature =>
          val newState =
            state
              .modify(_.temperatures.batteriesClosetTemperature)
              .setTo(temperature)
          val tempActions = Set(
            Action.SetOpenHabItemValue(
              config.batteryClosetTemperatureItem,
              temperature.toString
            )
          )

          // If automatic mode is enabled, check if battery fan should be controlled
          val automaticActions =
            if (state.fans.fanManagementAutomatic == Switch.On) {
              automaticBatteryFanControl(newState, timestamp)
            } else Set.empty

          (newState, tempActions ++ automaticActions)

        case Event.Temperature.ElectronicsTemperatureMeasured(temperature)
            if temperature != state.temperatures.electronicsTemperature =>
          val newState = state
            .modify(_.temperatures.electronicsTemperature)
            .setTo(temperature)
          val tempActions = Set(
            Action.SetOpenHabItemValue(
              config.electronicsTemperatureItem,
              temperature.toString
            )
          )

          // If automatic mode is enabled, check if electronics fan should be controlled
          val automaticActions =
            if (state.fans.fanManagementAutomatic == Switch.On) {
              automaticElectronicsFanControl(newState, timestamp)
            } else Set.empty

          (newState, tempActions ++ automaticActions)

        case Event.Temperature.ExternalTemperatureMeasured(temperature)
            if temperature != state.temperatures.externalTemperature =>
          val newState =
            state.modify(_.temperatures.externalTemperature).setTo(temperature)
          val tempActions = Set(
            Action.SetOpenHabItemValue(
              config.externalTemperatureItem,
              temperature.toString
            )
          )

          // If automatic mode is enabled, check if both fans should be controlled
          val automaticActions =
            if (state.fans.fanManagementAutomatic == Switch.On) {
              automaticBatteryFanControl(newState, timestamp) ++
                automaticElectronicsFanControl(newState, timestamp)
            } else Set.empty

          (newState, tempActions ++ automaticActions)

        case Event.Temperature.GoalTemperatureChanged(temperature)
            if temperature != state.temperatures.goalTemperature =>
          val newState =
            state.modify(_.temperatures.goalTemperature).setTo(temperature)

          // If automatic mode is enabled, re-evaluate both fans with new goal temperature
          val automaticActions =
            if (state.fans.fanManagementAutomatic == Switch.On) {
              automaticBatteryFanControl(newState, timestamp) ++
                automaticElectronicsFanControl(newState, timestamp)
            } else Set.empty

          (newState, automaticActions)

        case Event.Temperature.Fans.FanManagementChanged(status) =>
          val newState =
            state.modify(_.fans.fanManagementAutomatic).setTo(status)
          (
            newState,
            Set.empty[Action]
          )

        case Event.Temperature.Fans.BatteryFanSwitchReported(stateFan) =>
          val newState = state
            .modify(_.fans.fanBatteries)
            .using(_.process(RemoteState.Event(stateFan), timestamp))
          (
            newState,
            batteryFanActionProducer.produceActionsForConfirmed(
              newState.fans.fanBatteries,
              timestamp
            )
          )
        case Event.Temperature.Fans.ElectronicsFanSwitchReported(stateFan) =>
          val newState = state
            .modify(_.fans.fanElectronics)
            .using(_.process(RemoteState.Event(stateFan), timestamp))
          (
            newState,
            electronicsFanActionProducer.produceActionsForConfirmed(
              newState.fans.fanElectronics,
              timestamp
            )
          )
        case Event.Temperature.Fans.BatteryFanSwitchManualChanged(status) =>
          if (state.fans.fanManagementAutomatic == Switch.On) {
            // In automatic mode, manual changes don't control the remote switch
            // Only set back the OH item if the command differs from current state
            if (status != state.fans.fanBatteries.latestCommand) {
              (
                state,
                Set(
                  Action.SetOpenHabItemValue(
                    config.batteryFanCommandItem,
                    state.fans.fanBatteries.latestCommand.toStatusString
                  )
                )
              )
            } else {
              (state, Set.empty[Action])
            }
          } else {
            // In manual mode, proceed with normal behavior
            val newState = state
              .modify(_.fans.fanBatteries)
              .using(_.process(RemoteState.Command(status), timestamp))
            (
              newState,
              batteryFanActionProducer.produceActionsForCommand(
                newState.fans.fanBatteries,
                timestamp
              )
            )
          }
        case Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(status) =>
          if (state.fans.fanManagementAutomatic == Switch.On) {
            // In automatic mode, manual changes don't control the remote switch
            // Only set back the OH item if the command differs from current state
            if (status != state.fans.fanElectronics.latestCommand) {
              (
                state,
                Set(
                  Action.SetOpenHabItemValue(
                    config.electronicsFanCommandItem,
                    state.fans.fanElectronics.latestCommand.toStatusString
                  )
                )
              )
            } else {
              (state, Set.empty[Action])
            }
          } else {
            // In manual mode, proceed with normal behavior
            val newState = state
              .modify(_.fans.fanElectronics)
              .using(_.process(RemoteState.Command(status), timestamp))
            (
              newState,
              electronicsFanActionProducer.produceActionsForCommand(
                newState.fans.fanElectronics,
                timestamp
              )
            )
          }
        case Event.System.StartupEvent =>
          // Handle system startup: initialize inconsistency tracking and periodic actions
          val (newBatteryFan, batteryFanActions) = handleStartupForFan(
            state.fans.fanBatteries,
            timestamp,
            batteryFanActionProducer
          )
          val (newElectronicsFan, electronicsFanActions) = handleStartupForFan(
            state.fans.fanElectronics,
            timestamp,
            electronicsFanActionProducer
          )

          val finalState = state
            .modify(_.fans.fanBatteries)
            .setTo(newBatteryFan)
            .modify(_.fans.fanElectronics)
            .setTo(newElectronicsFan)

          (finalState, batteryFanActions ++ electronicsFanActions)
        case _ =>
          (
            state,
            Set.empty
          ) // needed for the case when temperatures don't change
      }
    }

    private def automaticFanControl(
        currentTemp: Double,
        externalTemp: Double,
        currentFanState: RemoteSwitch,
        goalTemp: Double,
        timestamp: Instant
    )(actionProducer: RemoteSwitch => Set[Action]): Set[Action] = {
      // Check if valid temperatures are available
      if (currentTemp > -100 && externalTemp > -100 && goalTemp > -100) {
        val shouldTurnOn = shouldTurnOnFan(currentTemp, externalTemp, goalTemp)
        val desiredStatus = if (shouldTurnOn) Switch.On else Switch.Off

        // Only send command if the desired state differs from current command
        if (desiredStatus != currentFanState.latestCommand) {
          val newFanState = currentFanState.process(
            RemoteState.Command(desiredStatus),
            timestamp
          )
          actionProducer(newFanState)
        } else Set.empty
      } else Set.empty
    }

    private def automaticBatteryFanControl(
        state: State,
        timestamp: Instant
    ): Set[Action] = {
      automaticFanControl(
        state.temperatures.batteriesClosetTemperature,
        state.temperatures.externalTemperature,
        state.fans.fanBatteries,
        state.temperatures.goalTemperature,
        timestamp
      )(batteryFanActionProducer.produceActionsForCommand(_, timestamp))
    }

    private def automaticElectronicsFanControl(
        state: State,
        timestamp: Instant
    ): Set[Action] = {
      automaticFanControl(
        state.temperatures.electronicsTemperature,
        state.temperatures.externalTemperature,
        state.fans.fanElectronics,
        state.temperatures.goalTemperature,
        timestamp
      )(electronicsFanActionProducer.produceActionsForCommand(_, timestamp))
    }

    private def shouldTurnOnFan(
        currentTemp: Double,
        externalTemp: Double,
        goalTemp: Double
    ): Boolean = {
      // Fan should turn on if external air would help move current temperature towards the goal
      if (currentTemp > goalTemp) {
        // Current temperature is too hot, fan helps if external air is cooler
        externalTemp < currentTemp
      } else if (currentTemp < goalTemp) {
        // Current temperature is too cold, fan helps if external air is warmer
        externalTemp > currentTemp
      } else {
        // Current temperature is at goal, no need for fan
        false
      }
    }

    private def handleStartupForFan(
        fanState: RemoteSwitch,
        timestamp: Instant,
        actionProducer: RemoteSwitchActionProducer
    ): (RemoteSwitch, Set[Action]) = {
      val actions = scala.collection.mutable.Set[Action]()

      // If there's an inconsistency timestamp, reset it to startup time to trigger timeout handling
      val updatedFanState = fanState.currentInconsistencyStart match {
        case Some(_) =>
          // Reset inconsistency start to current time so it will trigger timeout after the configured interval
          fanState.copy(currentInconsistencyStart = Some(timestamp))
        case None =>
          fanState
      }

      // Generate actions for the current state (this handles inconsistency tracking)
      actions ++= actionProducer.produceActionsForConfirmed(
        updatedFanState,
        timestamp
      )

      // Add periodic action for the last command
      if (fanState.latestCommand != fanState.confirmed) {
        actions ++= actionProducer.produceActionsForCommand(
          updatedFanState,
          timestamp
        )
      }

      (updatedFanState, actions.toSet)
    }

  }

  def apply(
      temperatureRelatedConfig: calespiga.config.TemperatureRelatedConfig
  ): StateProcessor.SingleProcessor = {
    val batteryFanProducer = RemoteStateActionProducer(
      temperatureRelatedConfig.batteryFanStatusItem,
      temperatureRelatedConfig.batteryFanMqttTopic,
      temperatureRelatedConfig.fansInconsistencyItem,
      temperatureRelatedConfig.batteryFanId,
      temperatureRelatedConfig.resendInterval,
      temperatureRelatedConfig.timeoutInterval
    )

    val electronicsFanProducer = RemoteStateActionProducer(
      temperatureRelatedConfig.electronicsFanStatusItem,
      temperatureRelatedConfig.electronicsFanMqttTopic,
      temperatureRelatedConfig.fansInconsistencyItem,
      temperatureRelatedConfig.electronicsFanId,
      temperatureRelatedConfig.resendInterval,
      temperatureRelatedConfig.timeoutInterval
    )

    Impl(
      batteryFanActionProducer = batteryFanProducer,
      electronicsFanActionProducer = electronicsFanProducer,
      config = temperatureRelatedConfig
    )
  }

  // For testing with custom action producers
  def apply(
      temperatureRelatedConfig: calespiga.config.TemperatureRelatedConfig,
      batteryFanActionProducer: RemoteSwitchActionProducer,
      electronicsFanActionProducer: RemoteSwitchActionProducer
  ): StateProcessor.SingleProcessor = {
    Impl(
      batteryFanActionProducer = batteryFanActionProducer,
      electronicsFanActionProducer = electronicsFanActionProducer,
      config = temperatureRelatedConfig
    )
  }

}
