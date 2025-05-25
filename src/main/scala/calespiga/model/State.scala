package calespiga.model

import calespiga.model.State.{Fans, Temperatures}

case class State(
    temperatures: Temperatures = Temperatures(),
    fans: Fans = Fans()
)

object State {

  case class Temperatures(
      externalTemperature: Double = -100,
      batteriesTemperature: Double = -100,
      electronicsTemperature: Double = -100
  )
  case class Fans(
      fanBatteries: Boolean = false,
      fanElectronics: Boolean = false
  )
}
