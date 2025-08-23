package calespiga.model

import java.time.Instant

object Fixture {

  val state: State = State(
    State.Temperatures(
      externalTemperature = 20.0,
      batteriesTemperature = 30.0,
      electronicsTemperature = 40.0
    ),
    State.Fans(
      fanBatteries = RemoteSwitch(),
      fanElectronics = RemoteSwitch()
    )
  )

  val now: Instant = Instant.now

  val event: Event = Event(
    timestamp = now,
    data = Event.Temperature.BatteryTemperatureMeasured(25.0)
  )

  val allEvents: List[Event] = List(
    Event(
      timestamp = now,
      data = Event.Temperature.BatteryTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.BatteryClosetTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.ElectronicsTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.ExternalTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.Fans.BatteryFanSwitchManualChanged(Switch.Off)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.Fans.BatteryFanSwitchReported(Switch.Off)
    ),
    Event(
      timestamp = now,
      data =
        Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(Switch.Off)
    ),
    Event(
      timestamp = now,
      data = Event.Temperature.Fans.ElectronicsFanSwitchReported(Switch.Off)
    )
  )
}
