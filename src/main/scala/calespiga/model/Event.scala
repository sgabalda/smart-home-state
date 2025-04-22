package calespiga.model

import calespiga.model.event.TemperatureRelated

import java.time.Instant

sealed trait Event {
  def timestamp: Instant
}

object Event {
  case class Temperature(
      timestamp: Instant,
      temperature: TemperatureRelated
  ) extends Event
}
