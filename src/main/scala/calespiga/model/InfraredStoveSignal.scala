package calespiga.model

import io.circe._

object InfraredStoveSignal {
  sealed trait UserCommand
  case object TurnOff extends UserCommand
  case object SetAutomatic extends UserCommand
  case object SetPower600 extends UserCommand
  case object SetPower1200 extends UserCommand

  // Encoder for UserCommand
  implicit val userCommandEncoder: Encoder[UserCommand] = Encoder.instance {
    s => Json.fromString(userCommandToString(s))
  }

  def userCommandToString(command: UserCommand): String = command match
    case TurnOff      => "off"
    case SetAutomatic => "automatic"
    case SetPower600  => "600"
    case SetPower1200 => "1200"

  // Decoder for UserCommand
  implicit val userCommandDecoder: Decoder[UserCommand] =
    Decoder.decodeString.emap {
      userCommandFromString
    }

  def userCommandFromString(str: String): Either[String, UserCommand] =
    str.toLowerCase match
      case "off"       => Right(TurnOff)
      case "automatic" => Right(SetAutomatic)
      case "600"       => Right(SetPower600)
      case "1200"      => Right(SetPower1200)
      case other       => Left(s"Invalid UserCommand: $other")

  sealed trait ControllerState {
    def power: Int
  }
  case object Off extends ControllerState {
    def power: Int = 0
  }
  case object Power600 extends ControllerState {
    def power: Int = 600
  }
  case object Power1200 extends ControllerState {
    def power: Int = 1200
  }

  implicit val controllerStateEncoder: Encoder[ControllerState] =
    Encoder.instance {
      case Off       => Json.fromString("Off")
      case Power600  => Json.fromString("600")
      case Power1200 => Json.fromString("1200")
    }

  implicit val controllerStateDecoder: Decoder[ControllerState] =
    Decoder.decodeString.emap {
      controllerStateFromString
    }

  def controllerStateFromString(str: String): Either[String, ControllerState] =
    str.toLowerCase match
      case "off"  => Right(Off)
      case "600"  => Right(Power600)
      case "1200" => Right(Power1200)
      case other  => Left(s"Invalid ControllerState: $other")

}
