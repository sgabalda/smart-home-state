package calespiga.model

import io.circe._
import sttp.tapir.Schema

enum OfflineOnlineSignal(val label: String) {
  case Online extends OfflineOnlineSignal("online")
  case Offline extends OfflineOnlineSignal("offline")
}

object OfflineOnlineSignal {
  implicit val encoder: Encoder[OfflineOnlineSignal] = Encoder.instance {
    case Online  => Json.fromString("online")
    case Offline => Json.fromString("offline")
  }

  implicit val decoder: Decoder[OfflineOnlineSignal] =
    Decoder.decodeString.emap {
      case s if s.equalsIgnoreCase("online") =>
        Right(OfflineOnlineSignal.Online)
      case s if s.equalsIgnoreCase("offline") =>
        Right(OfflineOnlineSignal.Offline)
      case other => Left(s"Invalid OfflineOnlineSignal: $other")
    }

  given Schema[OfflineOnlineSignal] = Schema.string
}
