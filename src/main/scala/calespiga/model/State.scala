package calespiga.model

import calespiga.model.State.{Fans, Temperatures}
import calespiga.model.RemoteSwitch.*

case class State(
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans()
)

object State {

  case class Temperatures(
      externalTemperature: Double = -100,
      batteriesTemperature: Double = -100,
      batteriesClosetTemperature: Double = -100,
      electronicsTemperature: Double = -100,
      goalTemperature: Double = -100
  )
  case class Fans(
      fanManagementAutomatic: Switch.Status = Switch.Off,
      fanBatteries: RemoteSwitch = RemoteSwitch(),
      fanElectronics: RemoteSwitch = RemoteSwitch()
  )
}
