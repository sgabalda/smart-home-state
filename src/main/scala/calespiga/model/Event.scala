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

    @InputEventOHItem("CalentadorHabilitatsSHS")
    case class SetHeaterManagement(enable: Boolean) extends FeatureFlagEvent
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

      @InputEventOHItem("VentiladorBateriesSetSHS")
      case class BatteryFanCommand(
          command: FanSignal.UserCommand
      ) extends FanData

      @InputEventMqtt("fan/batteries/status")
      case class BatteryFanStatus(
          status: FanSignal.ControllerState
      ) extends FanData

      @InputEventOHItem("VentiladorElectronicaSetSHS")
      case class ElectronicsFanCommand(
          command: FanSignal.UserCommand
      ) extends FanData

      @InputEventMqtt("fan/electronics/status")
      case class ElectronicsFanStatus(
          status: FanSignal.ControllerState
      ) extends FanData
    }
  }

  object Heater {
    sealed trait HeaterData extends EventData

    @InputEventMqtt("arduino_calentador/potencia/status")
    case class HeaterPowerStatusReported(
        status: HeaterSignal.ControllerState
    ) extends HeaterData

    @InputEventOHItem("CalentadorSetSHS")
    case class HeaterPowerCommandChanged(
        status: HeaterSignal.UserCommand
    ) extends HeaterData

    @InputEventMqtt("arduino_calentador/termostat/status")
    case class HeaterIsHotReported(
        status: HeaterSignal.HeaterTermostateState
    ) extends HeaterData
  }

  object Power {
    sealed trait PowerData extends EventData

    case class PowerProductionReported(
        powerAvailable: Float,
        powerProduced: Float,
        powerDiscarded: Float,
        linesPower: List[Float]
    ) extends PowerData
  }
}
