package calespiga.model.event

sealed trait TemperatureRelated

object TemperatureRelated {
  case class BatteryTemperatureMeasured(
      celsius: Double
  ) extends TemperatureRelated

  case class ElectronicsTemperatureMeasured(
      celsius: Double
  ) extends TemperatureRelated

  case class ExternalTemperatureMeasured(
      celsius: Double
  ) extends TemperatureRelated

  case class BatteryFanSwitchReported(
      on: Boolean
  ) extends TemperatureRelated

  case class ElectronicsFanSwitchReported(
      on: Boolean
  ) extends TemperatureRelated
}
