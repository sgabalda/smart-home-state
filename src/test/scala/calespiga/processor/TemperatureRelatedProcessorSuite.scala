package calespiga.processor

import calespiga.model.{Action, Event, Fixture, State}
import calespiga.model.Event.Temperature.*
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*
import calespiga.model.Switch
import java.time.Instant
import calespiga.model.RemoteSwitch
import calespiga.model.Action.SetOpenHabItemValue
import calespiga.model.Action.SendMqttStringMessage

class TemperatureRelatedProcessorSuite extends CatsEffectSuite {

  val now = Instant.now

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
                  "BateriesTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ BatteryClosetTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.batteriesClosetTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "BateriesTemperaturaAdosadaSHS",
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
              Set(
                Action.SetOpenHabItemValue(
                  "ElectronicaTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ ExternalTemperatureMeasured(_) =>
          List(
            (
              e.modify(_.celsius).setTo(11d),
              _.modify(_.temperatures.externalTemperature).setTo(11d),
              Set(
                Action.SetOpenHabItemValue(
                  "ExteriorArmarisTemperaturaSHS",
                  11d.toString
                )
              )
            )
          )
        case e @ Fans.BatteryFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.On, Switch.Off, Some(now))),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorBateriesStatusSHS",
                  value = "on"
                ),
                SendMqttStringMessage(
                  topic = "fan/batteries/set",
                  message = "stop"
                )
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorBateriesStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/batteries/set",
                  message = "stop"
                )
              )
            )
          )
        case e @ Fans.ElectronicsFanSwitchReported(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.On, Switch.Off, Some(now))),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorElectronicaStatusSHS",
                  value = "on"
                ),
                SendMqttStringMessage(
                  topic = "fan/electronics/set",
                  message = "stop"
                )
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorElectronicaStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/electronics/set",
                  message = "stop"
                )
              )
            )
          )
        case e @ Fans.BatteryFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.On, Some(now))),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorBateriesStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/batteries/set",
                  message = "start"
                )
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorBateriesStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/batteries/set",
                  message = "stop"
                )
              )
            )
          )
        case e @ Fans.ElectronicsFanSwitchManualChanged(_) =>
          List(
            (
              e.modify(_.status).setTo(Switch.On),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.On, Some(now))),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorElectronicaStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/electronics/set",
                  message = "start"
                )
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                SetOpenHabItemValue(
                  item = "VentiladorElectronicaStatusSHS",
                  value = "off"
                ),
                SendMqttStringMessage(
                  topic = "fan/electronics/set",
                  message = "stop"
                )
              )
            )
          )
      }
    )
    .foreach { (event, newState, expectedActions) =>
      test(
        s"TemperatureRelatedProcessor processes $event"
      ) {
        val sut = TemperatureRelatedProcessor()
        val (state, actions) = sut.process(Fixture.state, event, now)
        assertEquals(state, newState(Fixture.state))
        assertEquals(actions, expectedActions)
      }
    }
}
