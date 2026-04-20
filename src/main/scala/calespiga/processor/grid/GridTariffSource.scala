package calespiga.processor.grid

import calespiga.model.{Event, GridTariff}
import cats.effect.IO
import fs2.Stream
import java.time.ZoneId
import scala.concurrent.duration.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait GridTariffSource {
  def events: Stream[IO, Event.Grid.GridTariffChanged]
}

object GridTariffSource {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

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
            _ <- logger.info(
              s"At ${now.atZone(zone)}, next grid tariff change at ${next.atZone(zone)} (in ${delay.millis})"
            )
            _ <- IO.sleep(delay.millis)
            t <- IO.realTimeInstant
            tariff = GridTariff.at(t, zone)
            _ <- logger.info(
              s"At ${t.atZone(zone)}, grid tariff changed to $tariff"
            )
          } yield Event.Grid.GridTariffChanged(tariff)
        )
  }

  def apply(zone: ZoneId): GridTariffSource = Impl(zone)
}
