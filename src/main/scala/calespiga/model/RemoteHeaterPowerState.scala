package calespiga.model

import io.circe.{Encoder, Decoder}
import scala.util.Try

object RemoteHeaterPowerState {

  enum RemoteHeaterPowerStatus {
    case Off, Power500, Power1000, Power2000
  }

  // Encoder for Status
  implicit val statusEncoder: Encoder[RemoteHeaterPowerStatus] =
    Encoder.encodeString.contramap(_.toString)

  // Decoder for Status
  implicit val statusDecoder: Decoder[RemoteHeaterPowerStatus] =
    Decoder.decodeString.emap { str =>
      Try(RemoteHeaterPowerStatus.valueOf(str)).toEither.left.map(_ =>
        s"Invalid Status: $str"
      )
    }

  type RemoteHeaterPowerState = RemoteState[RemoteHeaterPowerStatus]

  def apply(
      confirmed: RemoteHeaterPowerStatus = RemoteHeaterPowerStatus.Off,
      latestCommand: RemoteHeaterPowerStatus = RemoteHeaterPowerStatus.Off,
      currentInconsistencyStart: Option[java.time.Instant] = None
  ): RemoteHeaterPowerState =
    RemoteState(confirmed, latestCommand, currentInconsistencyStart)

}
