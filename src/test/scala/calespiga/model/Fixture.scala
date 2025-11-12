package calespiga.model

import java.time.Instant

object Fixture {

  val state: State = State(
    State.FeatureFlags(fanManagementEnabled = true),
    State.Temperatures(
      externalTemperature = Some(20.0),
      batteriesTemperature = Some(30.0),
      electronicsTemperature = Some(40.0),
      goalTemperature = 20.0
    )
  )

  val now: Instant = Instant.now

  val event: Event = Event(
    timestamp = now,
    data = Event.Temperature.BatteryTemperatureMeasured(25.0)
  )
}
