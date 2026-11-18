package calespiga.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import sttp.tapir.Schema

sealed trait PondCirculationStatus

object PondCirculationStatus {
  case object PreparingDiposit extends PondCirculationStatus
  case object Diposit extends PondCirculationStatus
  case object PreparingDepuradora extends PondCirculationStatus
  case object Depuradora extends PondCirculationStatus
  case object PreparingOff extends PondCirculationStatus
  case object Off extends PondCirculationStatus

  def fromString(value: String): Option[PondCirculationStatus] =
    value.trim.toLowerCase match
      case "preparing_diposit"    => Some(PreparingDiposit)
      case "diposit"              => Some(Diposit)
      case "preparing_depuradora" => Some(PreparingDepuradora)
      case "depuradora"           => Some(Depuradora)
      case "preparing_off"        => Some(PreparingOff)
      case "off"                  => Some(Off)
      case _                      => None

  def toString(status: PondCirculationStatus): String = status match
    case PreparingDiposit    => "preparing_diposit"
    case Diposit             => "diposit"
    case PreparingDepuradora => "preparing_depuradora"
    case Depuradora          => "depuradora"
    case PreparingOff        => "preparing_off"
    case Off                 => "off"

  given Encoder[PondCirculationStatus] =
    Encoder.instance(status => Json.fromString(toString(status)))

  given Decoder[PondCirculationStatus] =
    Decoder.decodeString.emap { value =>
      fromString(value).toRight(s"Invalid pond circulation status: $value")
    }

  given Schema[PondCirculationStatus] = Schema.string
}
