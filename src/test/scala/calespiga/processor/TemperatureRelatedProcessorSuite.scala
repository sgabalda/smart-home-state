package calespiga.processor

import calespiga.model.{Fixture, State}
import calespiga.model.event.TemperatureRelated
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*

class TemperatureRelatedProcessorSuite extends CatsEffectSuite {

  Fixture.allTemperatureRelatedEvents
    .flatMap[(TemperatureRelated, State => State)](_.temperature match {
      case e @ TemperatureRelated.BatteryTemperatureMeasured(_) =>
        List(
          (
            e.modify(_.celsius).setTo(11d),
            _.modify(_.temperatures.batteriesTemperature).setTo(11d)
          )
        )
      case e @ TemperatureRelated.ElectronicsTemperatureMeasured(_) =>
        List(
          (
            e.modify(_.celsius).setTo(11d),
            _.modify(_.temperatures.electronicsTemperature).setTo(11d)
          )
        )
      case e @ TemperatureRelated.ExternalTemperatureMeasured(_) =>
        List(
          (
            e.modify(_.celsius).setTo(11d),
            _.modify(_.temperatures.externalTemperature).setTo(11d)
          )
        )
      case e @ TemperatureRelated.BatteryFanSwitchReported(_) =>
        List(
          (
            e.modify(_.on).setTo(true),
            _.modify(_.fans.fanBatteries).setTo(true)
          ),
          (
            e.modify(_.on).setTo(false),
            _.modify(_.fans.fanBatteries).setTo(false)
          )
        )
      case e @ TemperatureRelated.ElectronicsFanSwitchReported(_) =>
        List(
          (
            e.modify(_.on).setTo(true),
            _.modify(_.fans.fanElectronics).setTo(true)
          ),
          (
            e.modify(_.on).setTo(false),
            _.modify(_.fans.fanElectronics).setTo(false)
          )
        )
    })
    .foreach { (event, newState) =>
      test(
        s"TemperatureRelatedProcessor processes $event changing state and not causing actions"
      ) {
        val sut = TemperatureRelatedProcessor()
        val (state, actions) = sut.process(Fixture.state, event)
        assertEquals(state, newState(Fixture.state))
        assertEquals(actions, Set.empty)
      }
    }
}
