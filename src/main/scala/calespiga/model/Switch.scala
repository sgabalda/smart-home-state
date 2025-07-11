package calespiga.model

import io.circe._
import io.circe.generic.semiauto._

object Switch {
  sealed trait Status{
    def turn: Status = this match
      case On => Off
      case Off => On
  }
  case object On extends Status
  case object Off extends Status

  sealed trait Command
  case object Start extends Command
  case object Stop extends Command

  def statusFromString(str: String): Status = if(str.equalsIgnoreCase("on")) On else Off
  
  // Encoder for Status
  implicit val statusEncoder: Encoder[Status] = Encoder.instance {
    case On => Json.fromString("On")
    case Off => Json.fromString("Off")
  }

  // Decoder for Status
  implicit val statusDecoder: Decoder[Status] = Decoder.decodeString.emap {
    case "On" => Right(On)
    case "Off" => Right(Off)
    case other => Left(s"Invalid Status: $other")
  }

}
