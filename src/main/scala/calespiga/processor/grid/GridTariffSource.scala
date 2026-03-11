package calespiga.processor.grid

import calespiga.model.{Event, GridTariff}
import cats.effect.IO
import fs2.Stream
import java.time.ZoneId
import scala.concurrent.duration.*

trait GridTariffSource {
  def events: Stream[IO, Event.Grid.GridTariffChanged]
}

object GridTariffSource {

  private final case class Impl(zone: ZoneId) extends GridTariffSource {

    override def events: Stream[IO, Event.Grid.GridTariffChanged] =
      Stream.eval(
        IO.realTimeInstant.map(t =>
          Event.Grid.GridTariffChanged(GridTariff.at(t, zone))
        )
      ) ++
        Stream.repeatEval(
          for {
            now <- IO.realTimeInstant
            next = GridTariff.nextChangeInstant(now, zone)
            delay = math.max(0L, java.time.Duration.between(now, next).toMillis)
            _ <- IO.sleep(delay.millis)
            t <- IO.realTimeInstant
          } yield Event.Grid.GridTariffChanged(GridTariff.at(t, zone))
        )
  }

  def apply(zone: ZoneId): GridTariffSource = Impl(zone)
}
