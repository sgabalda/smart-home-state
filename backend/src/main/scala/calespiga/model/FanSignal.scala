package calespiga.model

import io.circe._

object FanSignal {

  sealed trait UserCommand
  case object TurnOff extends UserCommand
  case object TurnOn extends UserCommand
  case object SetAutomatic extends UserCommand

  implicit val userCommandEncoder: Encoder[UserCommand] = Encoder.instance {
    s => Json.fromString(userCommandToString(s))
  }

  def userCommandToString(command: UserCommand): String = command match
    case TurnOff      => "off"
    case SetAutomatic => "automatic"
    case TurnOn       => "on"

  // Decoder for UserCommand
  implicit val userCommandDecoder: Decoder[UserCommand] =
    Decoder.decodeString.emap {
      userCommandFromString
    }

  def userCommandFromString(str: String): Either[String, UserCommand] =
    str.toLowerCase match
      case "off"       => Right(TurnOff)
      case "automatic" => Right(SetAutomatic)
      case "on"        => Right(TurnOn)
      case other       => Left(s"Invalid UserCommand: $other")

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
      case other => Left(s"Invalid ControllerState: $other")

  def controllerStateToCommand(
      state: ControllerState
  ): String = state match
    case Off => "stop"
    case On  => "start"
}
