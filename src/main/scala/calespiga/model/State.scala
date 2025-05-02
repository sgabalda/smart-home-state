package calespiga.model

import calespiga.model.State.{Fans, Temperatures}

case class State(
    temperatures: Temperatures,
    fans: Fans
)

object State {

  val empty: State = State(Temperatures(-100, -100, -100), Fans(false, false))

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
