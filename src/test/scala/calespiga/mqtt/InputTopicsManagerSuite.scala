package calespiga.mqtt

import calespiga.model.Event
import munit.CatsEffectSuite

class InputTopicsManagerSuite extends CatsEffectSuite {

  test(
    "on batteries closet temperature topic it should decode the temperature"
  ) {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    conversions("diposit1/temperature/batteriescloset")(
      Vector[Byte](49, 54, 46, 57, 52)
    ) match {
      case Right(Event.Temperature.BatteryClosetTemperatureMeasured(temp)) =>
        assertEquals(temp, 16.94)
      case other =>
        fail(s"Conversion failed: obtained $other")
    }
  }

  test("on home energy topic it should decode accumulated watt-hours") {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    conversions("casa/consum/energy")(Vector[Byte](49, 50, 48, 48)) match {
      case Right(Event.Power.HomeConsumptionEnergyReported(energyWh)) =>
        assertEquals(energyWh, 1200f)
      case other =>
        fail(s"Conversion failed: obtained $other")
    }
  }

  test("on heater temperature topic it should decode the temperature") {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    conversions("arduino_calentador/temperature")(
      Vector[Byte](50, 49, 46, 53)
    ) match {
      case Right(Event.Heater.HeaterTemperatureMeasured(temperature)) =>
        assertEquals(temperature, 21.5)
      case other =>
        fail(s"Conversion failed: obtained $other")
    }
  }

  test("on garden pump status topic it should decode the reported status") {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    conversions("bassa/reg/status")(Vector[Byte](111, 110)) match {
      case Right(Event.PondPump.StatusReported(status)) =>
        assertEquals(status, "on")
      case other =>
        fail(s"Conversion failed: obtained $other")
    }
  }
}
