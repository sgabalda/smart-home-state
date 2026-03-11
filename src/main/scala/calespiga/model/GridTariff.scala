package calespiga.model

import io.circe.{Decoder, Encoder, Json}
import java.time.{DayOfWeek, Instant, ZoneId}
import sttp.tapir.Schema

/** Electricity tariff period for the grid connection. */
enum GridTariff(val label: String) {
  case Vall extends GridTariff("vall")
  case Pla extends GridTariff("pla")
  case Pic extends GridTariff("pic")
}

object GridTariff {

  implicit val encoder: Encoder[GridTariff] =
    Encoder.instance(t => Json.fromString(t.label))

  implicit val decoder: Decoder[GridTariff] =
    Decoder.decodeString.emap {
      case "vall" => Right(GridTariff.Vall)
      case "pla"  => Right(GridTariff.Pla)
      case "pic"  => Right(GridTariff.Pic)
      case other  => Left(s"Invalid GridTariff: $other")
    }

  given Schema[GridTariff] = Schema.string

  /** Returns the tariff active at the given instant in the specified timezone.
    */
  def at(instant: Instant, zone: ZoneId): GridTariff = {
    val zdt = instant.atZone(zone)
    zdt.getDayOfWeek match {
      case DayOfWeek.SATURDAY | DayOfWeek.SUNDAY => GridTariff.Vall
      case _                                     =>
        zdt.getHour match {
          case h if h < 8  => GridTariff.Vall
          case h if h < 10 => GridTariff.Pla
          case h if h < 14 => GridTariff.Pic
          case h if h < 18 => GridTariff.Pla
          case h if h < 22 => GridTariff.Pic
          case _           => GridTariff.Pla
        }
    }
  }

  /** Returns the next instant after `now` at which the tariff changes. */
  def nextChangeInstant(now: Instant, zone: ZoneId): Instant = {
    val currentTariff = at(now, zone)
    val boundaries = List(0, 8, 10, 14, 18, 22)
    val zdt = now.atZone(zone)

    (0 to 7)
      .flatMap { daysAhead =>
        val date = zdt.toLocalDate.plusDays(daysAhead)
        boundaries.map(hour => date.atTime(hour, 0).atZone(zone).toInstant)
      }
      .filter(_.isAfter(now))
      .find(instant => at(instant, zone) != currentTariff)
      .getOrElse(now.plusSeconds(3600)) // fallback, unreachable in practice
  }
}
