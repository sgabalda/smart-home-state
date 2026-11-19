package calespiga.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import sttp.tapir.Schema

sealed trait GreyWaterMode

object GreyWaterMode {
  case object On extends GreyWaterMode
  case object Off extends GreyWaterMode
  case object Horari extends GreyWaterMode

  def fromString(value: String): Option[GreyWaterMode] =
    value.trim.toLowerCase match
      case "on"     => Some(On)
      case "off"    => Some(Off)
      case "horari" => Some(Horari)
      case _        => None

  def toString(mode: GreyWaterMode): String = mode match
    case On     => "on"
    case Off    => "off"
    case Horari => "horari"

  given Encoder[GreyWaterMode] =
    Encoder.instance(mode => Json.fromString(toString(mode)))

  given Decoder[GreyWaterMode] = Decoder.decodeString.emap { value =>
    fromString(value).toRight(s"Invalid grey-water mode: $value")
  }

  given Schema[GreyWaterMode] = Schema.string
}
