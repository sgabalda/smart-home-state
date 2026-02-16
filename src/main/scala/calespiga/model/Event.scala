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

    @InputEventOHItem("EstufaInfrarrojosEnabledSHS")
    case class SetInfraredStoveEnabled(enable: Boolean) extends FeatureFlagEvent
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

  object InfraredStove {
    sealed trait InfraredStoveData extends EventData

    @InputEventMqtt("estufa1/status")
    case class InfraredStovePowerStatusReported(
        status: InfraredStoveSignal.ControllerState
    ) extends InfraredStoveData

    @InputEventOHItem("EstufaInfrarrojosSetSHS")
    case class InfraredStovePowerCommandChanged(
        status: InfraredStoveSignal.UserCommand
    ) extends InfraredStoveData

  }

  object Power {
    sealed trait PowerData extends EventData

    case class PowerProductionReported(
        powerAvailable: Float,
        powerProduced: Float,
        powerDiscarded: Float,
        linesPower: List[Float]
    ) extends PowerData

    case object PowerProductionReadingError extends PowerData

    // all implementations of the DynamicPowerData must be defined inside this object
    object DynamicPower {
      sealed trait DynamicPowerConsumerPriorityChanged extends PowerData {
        def consumerUniqueCode: String
        def priority: Int
      }

      // each event should have as a consumer code the OH item, and should be the same in all 3 places:
      // - the annotation
      // - the consumerUniqueCode value
      // - the property in the application.conf that is used in the DynamicPowerConsumer implementation.
      // this limitation will be overcome when getting rid of OH and having a custom UI.
      @InputEventOHItem("CalentadorPrioritatConsumSHS")
      case class HeaterPowerPriorityChanged(
          priority: Int
      ) extends DynamicPowerConsumerPriorityChanged {
        override val consumerUniqueCode: String = "CalentadorPrioritatConsumSHS"
      }
      @InputEventOHItem("EstufaInfrarrojosPrioritatConsumSHS")
      case class InfraredStovePowerPriorityChanged(
          priority: Int
      ) extends DynamicPowerConsumerPriorityChanged {
        override val consumerUniqueCode: String =
          "EstufaInfrarrojosPrioritatConsumSHS"
      }
    }
  }
}
