package calespiga.model

import calespiga.mqtt.annotations.InputEventMqtt
import calespiga.mqtt.annotations.InputTopicMapper
import calespiga.openhab.annotations.InputEventOHItem
import calespiga.openhab.annotations.InputOHItemsMapper
import java.time.Instant

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

  /** Use this only for internal events that are not triggered by external
    * inputs, but are needed for feedback loops or to trigger actions based on
    * time or other conditions. These events should not be created directly from
    * external inputs, but can be used internally in the application to
    * represent events that are not directly tied to an external input.
    */
  sealed trait FeedbackEventData extends EventData

  object System {
    sealed trait SystemData extends EventData

    // Event triggered when the system starts and state is restored from persistence
    case object StartupEvent extends SystemData

    case class OfflineDetected(id: String)
        extends FeedbackEventData
        with SystemData
  }

  object FeatureFlagEvents {
    sealed trait FeatureFlagEvent extends EventData

    @InputEventOHItem("BassaAiguaGrisaEnabledSHS")
    case class SetGreyWaterEnabled(enable: Boolean) extends FeatureFlagEvent

    @InputEventOHItem("RegarHortEnabledSHS")
    case class SetGardenWateringEnabled(enable: Boolean)
        extends FeatureFlagEvent
  }

  object Temperature {
    sealed trait TemperatureData extends EventData

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

    @InputEventMqtt("arduino_calentador/temperature")
    case class HeaterTemperatureMeasured(
        celsius: Double
    ) extends HeaterData

    @InputEventOHItem("CalentadorTemperaturaObjectiuSHS")
    case class HeaterGoalTemperatureChanged(
        celsius: Double
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

    case object InfraredStoveManualTimeExpired
        extends FeedbackEventData
        with InfraredStoveData

    @InputEventOHItem("EstufaInfrarrojosSetTempsManualSHS")
    case class InfraredStoveManualTimeChanged(
        minutes: Int
    ) extends InfraredStoveData

  }

  object GreyWater {
    sealed trait GreyWaterData extends EventData

    @InputEventMqtt("bassa/aiguagrisa/status")
    case class PumpStatusReported(status: String) extends GreyWaterData

    @InputEventOHItem("BassaAiguaGrisaModeSHS")
    case class ModeChanged(mode: String) extends GreyWaterData

    @InputEventOHItem("BassaAiguaGrisaHoraIniciSHS")
    case class StartHourChanged(hour: Int) extends GreyWaterData

    @InputEventOHItem("BassaAiguaGrisaHoraFiSHS")
    case class EndHourChanged(hour: Int) extends GreyWaterData

    case object ScheduleTransition extends FeedbackEventData with GreyWaterData
  }

  object GardenWatering {
    sealed trait GardenWateringData extends EventData

    @InputEventOHItem("RegarHortIniciarSHS")
    case class ManualWateringRequested(command: String)
        extends GardenWateringData

    @InputEventOHItem("RegarHortDuradaManualSHS")
    case class ManualDurationChanged(minutes: Int) extends GardenWateringData

    @InputEventOHItem("RegarHortDuradaProgramadaSHS")
    case class ScheduledDurationChanged(minutes: Int) extends GardenWateringData

    @InputEventOHItem("RegarHortIntervalProgramatSHS")
    case class PeriodHoursChanged(hours: Int) extends GardenWateringData

    @InputEventOHItem("RegarHortHoraIniciSHS")
    case class StartHourChanged(hour: Int) extends GardenWateringData

    case object ScheduledWateringDue
        extends FeedbackEventData
        with GardenWateringData

    case object WateringFinished
        extends FeedbackEventData
        with GardenWateringData
  }

  object Pump {
    sealed trait PumpEvent extends EventData

    case class CommandRequested(
        pumpId: String,
        on: Boolean,
        force: Boolean = false
    ) extends FeedbackEventData
        with PumpEvent
  }

  object PondPump {
    sealed trait PondPumpEvent extends EventData

    @InputEventMqtt("bassa/reg/status")
    case class StatusReported(status: String) extends PondPumpEvent
  }

  object Grid {
    sealed trait GridData extends EventData

    case class GridTariffChanged(tariff: GridTariff) extends GridData
  }

  object CarCharger {
    sealed trait CarChargerData extends EventData

    @InputEventMqtt("cotxe/carrega/status")
    case class CarChargerStatusReported(
        status: CarChargerSignal.ControllerState
    ) extends CarChargerData

    @InputEventMqtt("cotxe/carrega/power")
    case class CarChargerPowerReported(
        watts: Float
    ) extends CarChargerData

    @InputEventMqtt("cotxe/carrega/energy")
    case class CarChargerAccumulatedEnergyReported(
        totalWh: Float
    ) extends CarChargerData

    @InputEventOHItem("CarChargerSetSHS")
    case class CarChargerPowerCommandChanged(
        command: CarChargerSignal.UserCommand
    ) extends CarChargerData

    @InputEventOHItem("CarChargerMaxGridTariffSHS")
    case class CarChargerMaxGridTariffChanged(
        tariff: BatteryChargeTariff
    ) extends CarChargerData
  }

  object Power {
    sealed trait PowerData extends EventData

    @InputEventMqtt("casa/consum/power")
    case class HomeConsumptionPowerReported(powerConsumed: Float)
        extends PowerData

    @InputEventMqtt("casa/consum/energy")
    case class HomeConsumptionEnergyReported(energyWh: Float) extends PowerData

    object PowerStatusReported {
      case class PowerProductionReported(
          powerAvailable: Float,
          powerProduced: Float,
          powerDiscarded: Float,
          linesPower: List[Float]
      )

    }
    case class PowerStatusReported(
        production: Option[PowerStatusReported.PowerProductionReported],
        homeConsumption: Float,
        powerFromNetwork: Float
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
      @InputEventOHItem("CarChargerPrioritatConsumSHS")
      case class CarChargerPowerPriorityChanged(
          priority: Int
      ) extends DynamicPowerConsumerPriorityChanged {
        override val consumerUniqueCode: String =
          "CarChargerPrioritatConsumSHS"
      }
    }
  }
}
