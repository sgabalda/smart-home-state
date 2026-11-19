package calespiga.model

import io.circe._
import sttp.tapir.Schema

enum BatteryChargeTariff(val label: String) {
  case AllTariffs extends BatteryChargeTariff("all tariffs")
  case PlaAndVall extends BatteryChargeTariff("pla + vall")
  case Vall extends BatteryChargeTariff("vall")
  case NoneCharge extends BatteryChargeTariff("none")
}

object BatteryChargeTariff {
  def fromString(value: String): Option[BatteryChargeTariff] =
    value.toLowerCase match {
      case "all tariffs" => Some(AllTariffs)
      case "pla + vall"  => Some(PlaAndVall)
      case "vall"        => Some(Vall)
      case "none"        => Some(NoneCharge)
      case _             => None
    }

  implicit val encoder: Encoder[BatteryChargeTariff] = Encoder.instance {
    case AllTariffs => Json.fromString("all tariffs")
    case PlaAndVall => Json.fromString("pla + vall")
    case Vall       => Json.fromString("vall")
    case NoneCharge => Json.fromString("none")
  }

  implicit val decoder: Decoder[BatteryChargeTariff] =
    Decoder.decodeString.emap {
      case s if s.equalsIgnoreCase("all tariffs") =>
        Right(BatteryChargeTariff.AllTariffs)
      case s if s.equalsIgnoreCase("pla + vall") =>
        Right(BatteryChargeTariff.PlaAndVall)
      case s if s.equalsIgnoreCase("vall") => Right(BatteryChargeTariff.Vall)
      case s if s.equalsIgnoreCase("none") =>
        Right(BatteryChargeTariff.NoneCharge)
      case other => Left(s"Invalid BatteryChargeTariff: $other")
    }

  given Schema[BatteryChargeTariff] = Schema.string
}
