package calespiga.processor

import calespiga.model.Event.Temperature.TemperatureData
import calespiga.model.{Action, Event, Fixture, State}
import calespiga.model.Event.Temperature.*
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*
import calespiga.model.Switch

class TemperatureRelatedProcessorSuite extends CatsEffectSuite {

  Fixture.allTemperatureRelatedEvents
    .flatMap[(Event.Temperature.TemperatureData, State => State, Set[Action])](
      _.data match {
        case e @ BatteryTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.batteriesTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "BateriesTemperatura",
                  11d.toString
                )
              )
            )
          )
        case e @ ElectronicsTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.electronicsTemperature).setTo(11d),
              Set.empty
            )
          )
        case e @ ExternalTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.externalTemperature).setTo(11d),
              Set.empty
            )
          )
        case e @ Fans.BatteryFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanBatteries).setTo(Switch.On),
              Set.empty
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries).setTo(Switch.Off),
              Set.empty
            )
          )
        case e @ Fans.ElectronicsFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics).setTo(Switch.On),
              Set.empty
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics).setTo(Switch.Off),
              Set.empty
            )
          )
        case e @ Fans.BatteryFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics).setTo(Switch.Off),
              Set.empty
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics).setTo(Switch.Off),
              Set.empty
            )
          )
        case e @ Fans.ElectronicsFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics).setTo(Switch.Off),
              Set.empty
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics).setTo(Switch.Off),
              Set.empty // TODO add to the state remote switch
            )
          )
      }
    )
    .foreach { (event, newState, expectedActions) =>
      test(
        s"TemperatureRelatedProcessor processes $event changing state and not causing actions"
      ) {
        val sut = TemperatureRelatedProcessor()
        val (state, actions) = sut.process(Fixture.state, event)
        assertEquals(state, newState(Fixture.state))
        assertEquals(actions, expectedActions)
      }
    }
}
