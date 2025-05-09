package calespiga.mqtt

import calespiga.model.Event
import munit.CatsEffectSuite

class InputTopicsManagerSuite extends CatsEffectSuite {

  test(
    "on batteries temperature topic it should properly decode the temperature"
  ) {
    val conversions = InputTopicsManager.apply.inputTopicsConversions

    conversions("diposit1/temperature/batteries")(
      Vector[Byte](49, 54, 46, 57, 52)
    ) match {
      case Right(Event.Temperature.BatteryTemperatureMeasured(temperature)) =>
        assertEquals(temperature, 16.94)
      case other =>
        fail(s"Conversion failed: obtained $other")
    }
  }
}
