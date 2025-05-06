package calespiga.model

import java.time.Instant

case class Event(
    timestamp: Instant,
    data: Event.EventData
)

object Event {

  sealed trait EventData

  object Temperature {
    sealed trait TemperatureData extends EventData
    case class BatteryTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    case class ElectronicsTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    case class ExternalTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    object Fans {
      case class BatteryFanSwitchManualChanged(
          on: Boolean
      ) extends TemperatureData

      case class BatteryFanSwitchReported(
          on: Boolean
      ) extends TemperatureData

      case class ElectronicsFanSwitchManualChanged(
          on: Boolean
      ) extends TemperatureData

      case class ElectronicsFanSwitchReported(
          on: Boolean
      ) extends TemperatureData
    }
  }
}
