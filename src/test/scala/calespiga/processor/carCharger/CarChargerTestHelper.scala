package calespiga.processor.carCharger

import calespiga.model.{
  CarChargerChargingStatus,
  CarChargerSignal,
  OfflineOnlineSignal,
  State
}
import com.softwaremill.quicklens.*

import java.time.Instant

object CarChargerTestHelper {

  def stateWithCarCharger(
      switchStatus: Option[CarChargerSignal.ControllerState] = None,
      lastCommandSent: Option[CarChargerSignal.ControllerState] = None,
      lastCommandReceived: Option[CarChargerSignal.UserCommand] = None,
      lastChange: Option[Instant] = None,
      lastSyncing: Option[Instant] = None,
      currentPowerWatts: Option[Float] = None,
      currentDynamicFVPower: Option[Float] = None,
      currentDynamicGridPower: Option[Float] = None,
      maxGridTariff: Option[calespiga.model.BatteryChargeTariff] = None,
      lastEnergyUpdate: Option[Instant] = None,
      lastAccumulatedEnergyWh: Option[Float] = None,
      accumulatedAtDayStartWh: Option[Float] = None,
      online: Option[OfflineOnlineSignal] = None,
      chargingStatus: Option[CarChargerChargingStatus] = None
  ): State =
    State()
      .modify(_.carCharger)
      .setTo(
        State.CarCharger(
          switchStatus = switchStatus,
          lastCommandSent = lastCommandSent,
          lastCommandReceived = lastCommandReceived,
          lastChange = lastChange,
          lastSyncing = lastSyncing,
          currentPowerWatts = currentPowerWatts,
          currentDynamicFVPower = currentDynamicFVPower,
          currentDynamicGridPower = currentDynamicGridPower,
          maxGridTariff = maxGridTariff,
          lastEnergyUpdate = lastEnergyUpdate,
          lastAccumulatedEnergyWh = lastAccumulatedEnergyWh,
          accumulatedAtDayStartWh = accumulatedAtDayStartWh,
          online = online,
          chargingStatus = chargingStatus
        )
      )
}
