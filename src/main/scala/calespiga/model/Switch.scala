package calespiga.model

import io.circe._

object Switch {
  sealed trait Status {
    def toggle: Status = this match
      case On  => Off
      case Off => On

    def toCommandString: String = this match
      case On  => "start"
      case Off => "stop"

    def toStatusString: String = this match
      case On  => "on"
      case Off => "off"
  }
  case object On extends Status
  case object Off extends Status

  def statusFromString(str: String): Status =
    if (str.equalsIgnoreCase("on")) On else Off

  // Encoder for Status
  implicit val statusEncoder: Encoder[Status] = Encoder.instance {
    case On  => Json.fromString("On")
    case Off => Json.fromString("Off")
  }

  // Decoder for Status
  implicit val statusDecoder: Decoder[Status] = Decoder.decodeString.emap {
    case "On"  => Right(On)
    case "Off" => Right(Off)
    case other => Left(s"Invalid Status: $other")
  }

}
