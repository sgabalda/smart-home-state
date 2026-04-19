package calespiga.model

import io.circe._
import sttp.tapir.Schema

enum CarChargerChargingStatus(val label: String) {
  case Disabled extends CarChargerChargingStatus("disabled")
  case Connected extends CarChargerChargingStatus("connected")
  case Blocked extends CarChargerChargingStatus("blocked")
  case Charging extends CarChargerChargingStatus("charging")
}

object CarChargerChargingStatus {
  implicit val encoder: Encoder[CarChargerChargingStatus] = Encoder.instance {
    case Disabled  => Json.fromString("disabled")
    case Connected => Json.fromString("connected")
    case Blocked   => Json.fromString("blocked")
    case Charging  => Json.fromString("charging")
  }

  implicit val decoder: Decoder[CarChargerChargingStatus] =
    Decoder.decodeString.emap {
      case s if s.equalsIgnoreCase("disabled") =>
        Right(CarChargerChargingStatus.Disabled)
      case s if s.equalsIgnoreCase("connected") =>
        Right(CarChargerChargingStatus.Connected)
      case s if s.equalsIgnoreCase("blocked") =>
        Right(CarChargerChargingStatus.Blocked)
      case s if s.equalsIgnoreCase("charging") =>
        Right(CarChargerChargingStatus.Charging)
      case other => Left(s"Invalid CarChargerChargingStatus: $other")
    }

  given Schema[CarChargerChargingStatus] = Schema.string

  def chargingStatusToString(status: CarChargerChargingStatus): String =
    status match
      case Disabled  => "disabled"
      case Connected => "connected"
      case Blocked   => "blocked"
      case Charging  => "charging"
}
