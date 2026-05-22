package calespiga.model

import io.circe._
import sttp.tapir.Schema

object CarChargerSignal {

  sealed trait ControllerState
  case object Off extends ControllerState
  case object On extends ControllerState

  implicit val controllerStateEncoder: Encoder[ControllerState] =
    Encoder.instance {
      case Off => Json.fromString("off")
      case On  => Json.fromString("on")
    }

  implicit val controllerStateDecoder: Decoder[ControllerState] =
    Decoder.decodeString.emap {
      controllerStateFromString
    }

  def controllerStateFromString(str: String): Either[String, ControllerState] =
    str.toLowerCase match
      case "off" => Right(Off)
      case "on"  => Right(On)
      case other => Left(s"Invalid CarChargerSignal.ControllerState: $other")

  def controllerStateToString(state: ControllerState): String = state match
    case Off => "off"
    case On  => "on"

  def controllerStatePower(state: ControllerState): Int = state match
    case Off => 0
    case On  => 1

  // Commands coming from the UI (user intent)
  sealed trait UserCommand
  case object TurnOff extends UserCommand
  case object TurnOn extends UserCommand
  case object SetAutomatic extends UserCommand

  implicit val userCommandEncoder: Encoder[UserCommand] = Encoder.instance {
    c => Json.fromString(userCommandToString(c))
  }

  implicit val userCommandDecoder: Decoder[UserCommand] =
    Decoder.decodeString.emap {
      userCommandFromString
    }

  def userCommandToString(cmd: UserCommand): String = cmd match
    case TurnOff      => "off"
    case TurnOn       => "on"
    case SetAutomatic => "automatic"

  def userCommandFromString(str: String): Either[String, UserCommand] =
    str.toLowerCase match
      case "off"       => Right(TurnOff)
      case "on"        => Right(TurnOn)
      case "automatic" => Right(SetAutomatic)
      case other       => Left(s"Invalid CarChargerSignal.UserCommand: $other")

  given Schema[UserCommand] = Schema.string
}
