package calespiga.model

object Fixture {

  val state: State = State(
    State.Temperatures(
      externalTemperature = 20.0,
      batteriesTemperature = 30.0,
      electronicsTemperature = 40.0
    ),
    State.Fans(
      fanBatteries = Switch.Off,
      fanElectronics = Switch.Off
    )
  )

  val event: Event = Event(
    timestamp = java.time.Instant.now(),
    data = Event.Temperature.BatteryTemperatureMeasured(25.0)
  )

  val allTemperatureRelatedEvents: List[Event] = List(
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.BatteryTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.ElectronicsTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.ExternalTemperatureMeasured(25.0)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.Fans.BatteryFanSwitchManualChanged(Switch.Off)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.Fans.BatteryFanSwitchReported(Switch.Off)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.Fans.ElectronicsFanSwitchManualChanged(Switch.Off)
    ),
    Event(
      timestamp = java.time.Instant.now(),
      data = Event.Temperature.Fans.ElectronicsFanSwitchReported(Switch.Off)
    )
  )

}
