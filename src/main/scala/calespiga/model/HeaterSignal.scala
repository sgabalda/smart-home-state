package calespiga.model

import io.circe._

object HeaterSignal {
  sealed trait UserCommand
  case object TurnOff extends UserCommand
  case object SetAutomatic extends UserCommand
  case object SetPower500 extends UserCommand
  case object SetPower1000 extends UserCommand
  case object SetPower2000 extends UserCommand

  // Encoder for UserCommand
  implicit val userCommandEncoder: Encoder[UserCommand] = Encoder.instance {
    case TurnOff      => Json.fromString("Off")
    case SetAutomatic => Json.fromString("Automatic")
    case SetPower500  => Json.fromString("Power500")
    case SetPower1000 => Json.fromString("Power1000")
    case SetPower2000 => Json.fromString("Power2000")
  }

  // Decoder for UserCommand
  implicit val userCommandDecoder: Decoder[UserCommand] =
    Decoder.decodeString.emap {
      userCommandFromString
    }

  def userCommandFromString(str: String): Either[String, UserCommand] =
    str match
      case "Off"       => Right(TurnOff)
      case "Automatic" => Right(SetAutomatic)
      case "Power500"  => Right(SetPower500)
      case "Power1000" => Right(SetPower1000)
      case "Power2000" => Right(SetPower2000)
      case other       => Left(s"Invalid UserCommand: $other")

  sealed trait ControllerState {
    def power: Int
  }
  case object Off extends ControllerState {
    def power: Int = 0
  }
  case object Power500 extends ControllerState {
    def power: Int = 500
  }
  case object Power1000 extends ControllerState {
    def power: Int = 1000
  }
  case object Power2000 extends ControllerState {
    def power: Int = 2000
  }

  implicit val controllerStateEncoder: Encoder[ControllerState] =
    Encoder.instance {
      case Off       => Json.fromString("Off")
      case Power500  => Json.fromString("Power500")
      case Power1000 => Json.fromString("Power1000")
      case Power2000 => Json.fromString("Power2000")
    }

  implicit val controllerStateDecoder: Decoder[ControllerState] =
    Decoder.decodeString.emap {
      controllerStateFromString
    }

  def controllerStateFromString(str: String): Either[String, ControllerState] =
    str match
      case "Off"       => Right(Off)
      case "Power500"  => Right(Power500)
      case "Power1000" => Right(Power1000)
      case "Power2000" => Right(Power2000)
      case other       => Left(s"Invalid ControllerState: $other")

}
