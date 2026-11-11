package calespiga.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import sttp.tapir.Schema

sealed trait PondCirculationMode

object PondCirculationMode {
  case object Depuradora extends PondCirculationMode
  case object Diposit extends PondCirculationMode

  def fromString(value: String): Option[PondCirculationMode] =
    value.trim.toLowerCase match
      case "depuradora" => Some(Depuradora)
      case "diposit"    => Some(Diposit)
      case _            => None

  def toString(mode: PondCirculationMode): String = mode match
    case Depuradora => "depuradora"
    case Diposit    => "diposit"

  given Encoder[PondCirculationMode] =
    Encoder.instance(mode => Json.fromString(toString(mode)))

  given Decoder[PondCirculationMode] =
    Decoder.decodeString.emap { value =>
      fromString(value).toRight(s"Invalid pond circulation mode: $value")
    }

  given Schema[PondCirculationMode] = Schema.string
}
