package calespiga.model

import io.circe._
import sttp.tapir.Schema

enum BatteryStatus(val label: String) {
  case Low extends BatteryStatus("low")
  case Medium extends BatteryStatus("medium")
  case High extends BatteryStatus("high")
}

object BatteryStatus {
  def fromString(value: String): Option[BatteryStatus] =
    value.toLowerCase match {
      case "low"    => Some(Low)
      case "medium" => Some(Medium)
      case "high"   => Some(High)
      case _        => None
    }

  implicit val encoder: Encoder[BatteryStatus] = Encoder.instance {
    case Low    => Json.fromString("low")
    case Medium => Json.fromString("medium")
    case High   => Json.fromString("high")
  }

  implicit val decoder: Decoder[BatteryStatus] = Decoder.decodeString.emap {
    case s if s.equalsIgnoreCase("low")    => Right(BatteryStatus.Low)
    case s if s.equalsIgnoreCase("medium") => Right(BatteryStatus.Medium)
    case s if s.equalsIgnoreCase("high")   => Right(BatteryStatus.High)
    case other => Left(s"Invalid BatteryStatus: $other")
  }

  given Schema[BatteryStatus] = Schema.string
}
