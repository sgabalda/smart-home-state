package calespiga.model

object Fixture {

  val state: State = State(
    State.Temperatures(
      externalTemperature = 20.0,
      batteriesTemperature = 30.0,
      electronicsTemperature = 40.0
    ),
    State.Fans(
      fanBatteries = true,
      fanElectronics = false
    )
  )

  val event: Event.Temperature = Event.Temperature(
    timestamp = java.time.Instant.now(),
    temperature =
      calespiga.model.event.TemperatureRelated.BatteryTemperatureMeasured(25.0)
  )

  val allTemperatureRelatedEvents: List[Event.Temperature] = List(
    Event.Temperature(
      timestamp = java.time.Instant.now(),
      temperature = calespiga.model.event.TemperatureRelated
        .BatteryTemperatureMeasured(25.0)
    ),
    Event.Temperature(
      timestamp = java.time.Instant.now(),
      temperature = calespiga.model.event.TemperatureRelated
        .ElectronicsTemperatureMeasured(35.0)
    ),
    Event.Temperature(
      timestamp = java.time.Instant.now(),
      temperature = calespiga.model.event.TemperatureRelated
        .ExternalTemperatureMeasured(15.0)
    ),
    Event.Temperature(
      timestamp = java.time.Instant.now(),
      temperature =
        calespiga.model.event.TemperatureRelated.BatteryFanSwitchReported(true)
    ),
    Event.Temperature(
      timestamp = java.time.Instant.now(),
      temperature = calespiga.model.event.TemperatureRelated
        .ElectronicsFanSwitchReported(false)
    )
  )

}
