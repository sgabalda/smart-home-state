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

  test("on pond measurement topics it should decode the reported values") {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    assertEquals(
      conversions("bassa/percentage")(Vector[Byte](55, 48, 46, 53)),
      Right(Event.PondPump.PercentageReported(70.5))
    )
    assertEquals(
      conversions("bassa/liters")(Vector[Byte](49, 50, 51, 46, 52)),
      Right(Event.PondPump.LitersReported(123.4))
    )
    assertEquals(
      conversions("bassa/height")(Vector[Byte](52, 50, 46, 48)),
      Right(Event.PondPump.HeightReported(42.0))
    )
  }
}
