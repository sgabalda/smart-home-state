package calespiga.model

import io.circe._

object RemoteHeaterPowerState {

  sealed trait RemoteHeaterPowerStatus

  case object Off extends RemoteHeaterPowerStatus
  case object Power500 extends RemoteHeaterPowerStatus
  case object Power1000 extends RemoteHeaterPowerStatus
  case object Power2000 extends RemoteHeaterPowerStatus

  // Encoder for Status
  implicit val statusEncoder: Encoder[RemoteHeaterPowerStatus] =
    Encoder.instance {
      case Off       => Json.fromString("Off")
      case Power500  => Json.fromString("Power500")
      case Power1000 => Json.fromString("Power1000")
      case Power2000 => Json.fromString("Power2000")
    }

  // Decoder for Status
  implicit val statusDecoder: Decoder[RemoteHeaterPowerStatus] =
    Decoder.decodeString.emap {
      case "Off"       => Right(Off)
      case "Power500"  => Right(Power500)
      case "Power1000" => Right(Power1000)
      case "Power2000" => Right(Power2000)
      case other       => Left(s"Invalid Status: $other")
    }

  type RemoteHeaterPowerState = RemoteState[RemoteHeaterPowerStatus]

  def apply(
      confirmed: RemoteHeaterPowerStatus = Off,
      latestCommand: RemoteHeaterPowerStatus = Off,
      currentInconsistencyStart: Option[java.time.Instant] = None
  ): RemoteHeaterPowerState =
    RemoteState(confirmed, latestCommand, currentInconsistencyStart)

}
