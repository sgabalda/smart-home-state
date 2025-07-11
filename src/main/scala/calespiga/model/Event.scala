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

  object Temperature {
    sealed trait TemperatureData extends EventData

    @InputEventMqtt("diposit1/temperature/batteries")
    case class BatteryTemperatureMeasured(
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

    object Fans {
      sealed trait FanData extends TemperatureData

      @InputEventOHItem("VentiladorBateriesManual")
      case class BatteryFanSwitchManualChanged(
          on: Boolean
      ) extends FanData

      @InputEventMqtt("fan/batteries/status")
      case class BatteryFanSwitchReported(
          on: Boolean
      ) extends FanData

      @InputEventOHItem("VentiladorElectronicaManual")
      case class ElectronicsFanSwitchManualChanged(
          on: Boolean
      ) extends FanData
      @InputEventMqtt("fan/electronics/status")
      case class ElectronicsFanSwitchReported(
          on: Boolean
      ) extends FanData
    }
  }
}
