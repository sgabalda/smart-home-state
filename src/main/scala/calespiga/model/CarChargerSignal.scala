package calespiga.model

import io.circe._

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
}
