package calespiga.model

import calespiga.model.State.{Fans, Temperatures}
import calespiga.model.RemoteSwitch.*

case class State(
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans()
)

object State {

  val sentinelTemp = -100.0
  case class Temperatures(
      externalTemperature: Double = sentinelTemp,
      batteriesTemperature: Double = sentinelTemp,
      batteriesClosetTemperature: Double = sentinelTemp,
      electronicsTemperature: Double = sentinelTemp,
      goalTemperature: Double = sentinelTemp
  )
  case class Fans(
      fanManagementAutomatic: Switch.Status = Switch.Off,
      fanBatteries: RemoteSwitch = RemoteSwitch(),
      fanElectronics: RemoteSwitch = RemoteSwitch()
  )
}
