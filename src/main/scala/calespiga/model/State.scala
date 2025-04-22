package calespiga.model

import calespiga.model.State.{Fans, Temperatures}

case class State(
    temperatures: Temperatures,
    fans: Fans
)

object State {
  case class Temperatures(
      externalTemperature: Double,
      batteriesTemperature: Double,
      electronicsTemperature: Double
  )
  case class Fans(
      fanBatteries: Boolean,
      fanElectronics: Boolean
  )
}
