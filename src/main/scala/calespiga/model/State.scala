package calespiga.model

import calespiga.model.State.{Fans, Temperatures, FeatureFlags}
import calespiga.model.RemoteSwitch.*

case class State(
    featureFlags: FeatureFlags = FeatureFlags(),
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans()
)

object State {

  case class Temperatures(
      externalTemperature: Option[Double] = None,
      batteriesTemperature: Option[Double] = None,
      batteriesClosetTemperature: Option[Double] = None,
      electronicsTemperature: Option[Double] = None,
      goalTemperature: Option[Double] = None
  )
  case class Fans(
      fanManagementAutomatic: Switch.Status = Switch.Off,
      fanBatteries: RemoteSwitch = RemoteSwitch(),
      fanElectronics: RemoteSwitch = RemoteSwitch()
  )

  case class FeatureFlags(
      fanManagementEnabled: Boolean =
        false // to be removed when fans are controled by SHS
  )
}
