package calespiga.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import sttp.tapir.Schema

sealed trait PondCirculationCommand

object PondCirculationCommand {
  case object Depuradora extends PondCirculationCommand
  case object Diposit extends PondCirculationCommand
  case object Stop extends PondCirculationCommand

  def fromString(value: String): Option[PondCirculationCommand] =
    value.trim.toLowerCase match
      case "depuradora" => Some(Depuradora)
      case "diposit"    => Some(Diposit)
      case "stop"       => Some(Stop)
      case _            => None

  def toString(command: PondCirculationCommand): String = command match
    case Depuradora => "depuradora"
    case Diposit    => "diposit"
    case Stop       => "stop"

  given Encoder[PondCirculationCommand] =
    Encoder.instance(command => Json.fromString(toString(command)))

  given Decoder[PondCirculationCommand] =
    Decoder.decodeString.emap { value =>
      fromString(value).toRight(s"Invalid pond circulation command: $value")
    }

  given Schema[PondCirculationCommand] = Schema.string
}
