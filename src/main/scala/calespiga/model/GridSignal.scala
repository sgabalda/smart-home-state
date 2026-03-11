package calespiga.model

import io.circe._
import sttp.tapir.Schema

object GridSignal {

  sealed trait ActorsConnecting
  case object Manual extends ActorsConnecting
  case object Car extends ActorsConnecting
  case object Batteries extends ActorsConnecting

  sealed trait ControllerState
  case object Connected extends ControllerState
  case object Disconnected extends ControllerState

  /** Converts a controller state to the MQTT command string to send to the
    * device.
    */
  def toMqttCommand(state: ControllerState): String = state match
    case Connected    => "start"
    case Disconnected => "stop"

  /** Parses the controller state from the MQTT status topic payload ("on" /
    * "off").
    */
  def controllerStateFromString(
      str: String
  ): Either[String, ControllerState] =
    str.toLowerCase match
      case "on"  => Right(Connected)
      case "off" => Right(Disconnected)
      case other => Left(s"Invalid GridSignal.ControllerState: $other")

  def controllerStateToUiString(state: ControllerState): String = state match
    case Connected    => "on"
    case Disconnected => "off"

  implicit val controllerStateEncoder: Encoder[ControllerState] =
    Encoder.instance {
      case Connected    => Json.fromString("on")
      case Disconnected => Json.fromString("off")
    }

  implicit val controllerStateDecoder: Decoder[ControllerState] =
    Decoder.decodeString.emap(controllerStateFromString)

  implicit val actorsConnectingEncoder: Encoder[ActorsConnecting] =
    Encoder.instance {
      case Manual    => Json.fromString("manual")
      case Car       => Json.fromString("car")
      case Batteries => Json.fromString("batteries")
    }

  implicit val actorsConnectingDecoder: Decoder[ActorsConnecting] =
    Decoder.decodeString.emap {
      case "manual"    => Right(Manual)
      case "car"       => Right(Car)
      case "batteries" => Right(Batteries)
      case other       => Left(s"Invalid GridSignal.ActorsConnecting: $other")
    }

  given Schema[GridSignal.ActorsConnecting] = Schema.string

}
