package calespiga.processor

import calespiga.model.Event.Temperature.TemperatureData
import calespiga.model.{Action, Event, Fixture, State}
import calespiga.model.Event.Temperature.*
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*

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
              e.modify(_.on).setTo(true),
              _.modify(_.fans.fanBatteries).setTo(true),
              Set.empty
            ),
            (
              e.modify(_.on).setTo(false),
              _.modify(_.fans.fanBatteries).setTo(false),
              Set.empty
            )
          )
        case e @ Fans.ElectronicsFanSwitchReported(_) =>
          List(
            (
              e.modify(_.on).setTo(true),
              _.modify(_.fans.fanElectronics).setTo(true),
              Set.empty
            ),
            (
              e.modify(_.on).setTo(false),
              _.modify(_.fans.fanElectronics).setTo(false),
              Set.empty
            )
          )
        case e @ Fans.BatteryFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.on).setTo(true),
              _.modify(_.fans.fanElectronics).setTo(false),
              Set.empty
            ),
            (
              e.modify(_.on).setTo(false),
              _.modify(_.fans.fanElectronics).setTo(false),
              Set.empty
            )
          )
        case e @ Fans.ElectronicsFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.on).setTo(true),
              _.modify(_.fans.fanElectronics).setTo(false),
              Set.empty
            ),
            (
              e.modify(_.on).setTo(false),
              _.modify(_.fans.fanElectronics).setTo(false),
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
