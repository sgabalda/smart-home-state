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
    s => Json.fromString(userCommandToString(s))
  }

  def userCommandToString(command: UserCommand): String = command match
    case TurnOff      => "off"
    case SetAutomatic => "automatic"
    case SetPower500  => "500"
    case SetPower1000 => "1000"
    case SetPower2000 => "2000"

  // Decoder for UserCommand
  implicit val userCommandDecoder: Decoder[UserCommand] =
    Decoder.decodeString.emap {
      userCommandFromString
    }

  def userCommandFromString(str: String): Either[String, UserCommand] =
    str.toLowerCase match
      case "off"       => Right(TurnOff)
      case "automatic" => Right(SetAutomatic)
      case "500"       => Right(SetPower500)
      case "1000"      => Right(SetPower1000)
      case "2000"      => Right(SetPower2000)
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
      case Power500  => Json.fromString("500")
      case Power1000 => Json.fromString("1000")
      case Power2000 => Json.fromString("2000")
    }

  implicit val controllerStateDecoder: Decoder[ControllerState] =
    Decoder.decodeString.emap {
      controllerStateFromString
    }

  def controllerStateFromString(str: String): Either[String, ControllerState] =
    str.toLowerCase match
      case "off"  => Right(Off)
      case "500"  => Right(Power500)
      case "1000" => Right(Power1000)
      case "2000" => Right(Power2000)
      case other  => Left(s"Invalid ControllerState: $other")

  sealed trait HeaterTermostateState
  case object Hot extends HeaterTermostateState
  case object Cold extends HeaterTermostateState

  implicit val heaterTermostateStateEncoder: Encoder[HeaterTermostateState] =
    Encoder.instance {
      case Hot  => Json.fromString("HOT")
      case Cold => Json.fromString("COLD")
    }

  def heaterTermostateStateFromString(
      str: String
  ): Either[String, HeaterTermostateState] =
    str.toUpperCase match
      case "HOT"  => Right(Hot)
      case "COLD" => Right(Cold)
      case other  => Left(s"Invalid HeaterTermostateState: $other")

  implicit val heaterTermostateStateDecoder: Decoder[HeaterTermostateState] =
    Decoder.decodeString.emap {
      heaterTermostateStateFromString
    }
}
