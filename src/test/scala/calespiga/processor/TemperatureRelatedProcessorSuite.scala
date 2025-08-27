package calespiga.processor

import calespiga.model.{Action, Event, Fixture, State, RemoteState}
import calespiga.model.Event.Temperature.*
import munit.CatsEffectSuite
import com.softwaremill.quicklens.*
import calespiga.model.Switch
import java.time.Instant
import calespiga.model.RemoteSwitch
import calespiga.processor.RemoteStateActionProducer.*

class TemperatureRelatedProcessorSuite extends CatsEffectSuite {

  val now = Instant.now

  // Stub action producers that return predictable, simple actions for testing isolation
  private val batteryFanActionProducerStub: RemoteSwitchActionProducer = 
    new RemoteStateActionProducer[Switch.Status] {
      def produceActionsForConfirmed(remoteState: RemoteState[Switch.Status], timestamp: Instant): Set[Action] =
        Set(Action.SetOpenHabItemValue("battery-fan-confirmed", remoteState.confirmed.toStatusString))
      
      def produceActionsForCommand(remoteState: RemoteState[Switch.Status], timestamp: Instant): Set[Action] =
        Set(Action.SetOpenHabItemValue("battery-fan-command", remoteState.latestCommand.toCommandString))
    }

  private val electronicsFanActionProducerStub: RemoteSwitchActionProducer = 
    new RemoteStateActionProducer[Switch.Status] {
      def produceActionsForConfirmed(remoteState: RemoteState[Switch.Status], timestamp: Instant): Set[Action] =
        Set(Action.SetOpenHabItemValue("electronics-fan-confirmed", remoteState.confirmed.toStatusString))
      
      def produceActionsForCommand(remoteState: RemoteState[Switch.Status], timestamp: Instant): Set[Action] =
        Set(Action.SetOpenHabItemValue("electronics-fan-command", remoteState.latestCommand.toCommandString))
    }

  Fixture.allEvents
    .flatMap[(Event.EventData, State => State, Set[Action])](
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
                Action.SetOpenHabItemValue("battery-fan-confirmed", "on")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("battery-fan-confirmed", "off")
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
                Action.SetOpenHabItemValue("electronics-fan-confirmed", "on")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-confirmed", "off")
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
                Action.SetOpenHabItemValue("battery-fan-command", "start")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanBatteries)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("battery-fan-command", "stop")
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
                Action.SetOpenHabItemValue("electronics-fan-command", "start")
              )
            ),
            (
              e.modify(_.status).setTo(Switch.Off),
              _.modify(_.fans.fanElectronics)
                .setTo(RemoteSwitch(Switch.Off, Switch.Off)),
              Set(
                Action.SetOpenHabItemValue("electronics-fan-command", "stop")
              )
            )
          )
        // case e => List((e, s => s, Set.empty)) for when more events are to be defined
      }
    )
    .foreach { (event, newState, expectedActions) =>
      test(
        s"TemperatureRelatedProcessor processes $event"
      ) {
        val sut = TemperatureRelatedProcessor(
          batteryFanActionProducer = batteryFanActionProducerStub,
          electronicsFanActionProducer = electronicsFanActionProducerStub
        )
        val (state, actions) = sut.process(Fixture.state, event, now)
        assertEquals(state, newState(Fixture.state))
        assertEquals(actions, expectedActions)
      }
    }
}
