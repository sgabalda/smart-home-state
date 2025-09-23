package calespiga.model

import calespiga.mqtt.annotations.InputEventMqtt

import java.time.Instant
import calespiga.mqtt.annotations.InputTopicMapper
import calespiga.openhab.annotations.InputEventOHItem
import calespiga.openhab.annotations.InputOHItemsMapper

case class Event(
    timestamp: Instant,
    data: Event.EventData
)

object Event {

  lazy val eventsMqttMessagesConverter: List[(String, String => EventData)] =
    InputTopicMapper.generateTopicMap()
  lazy val eventsOpenHabInputItemsConverter
      : List[(String, String => EventData)] =
    InputOHItemsMapper.generateItemsMap()

  sealed trait EventData

  object System {
    sealed trait SystemData extends EventData

    // Event triggered when the system starts and state is restored from persistence
    case object StartupEvent extends SystemData
  }

  object FeatureFlagEvents {
    sealed trait FeatureFlagEvent extends EventData

    @InputEventOHItem("GestioVentiladorsSHS")
    case class SetFanManagement(enable: Boolean) extends FeatureFlagEvent
  }

  object Temperature {
    sealed trait TemperatureData extends EventData

    @InputEventMqtt("diposit1/temperature/batteries")
    case class BatteryTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    @InputEventMqtt("diposit1/temperature/batteriescloset")
    case class BatteryClosetTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    @InputEventMqtt("diposit1/temperature/electronics")
    case class ElectronicsTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    @InputEventMqtt("diposit1/temperature/outdoor")
    case class ExternalTemperatureMeasured(
        celsius: Double
    ) extends TemperatureData

    @InputEventOHItem("TemperaturaObjectiuSHS")
    case class GoalTemperatureChanged(
        celsius: Double
    ) extends TemperatureData

    object Fans {
      sealed trait FanData extends TemperatureData

      @InputEventOHItem("VentiladorGestio")
      case class FanManagementChanged(
          status: Switch.Status
      ) extends FanData

      @InputEventOHItem("VentiladorBateriesSetSHS")
      case class BatteryFanSwitchManualChanged(
          status: Switch.Status
      ) extends FanData

      @InputEventMqtt("fan/batteries/status")
      case class BatteryFanSwitchReported(
          status: Switch.Status
      ) extends FanData

      @InputEventOHItem("VentiladorElectronicaSetSHS")
      case class ElectronicsFanSwitchManualChanged(
          status: Switch.Status
      ) extends FanData

      @InputEventMqtt("fan/electronics/status")
      case class ElectronicsFanSwitchReported(
          status: Switch.Status
      ) extends FanData
    }
  }
}
