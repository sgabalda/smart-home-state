package calespiga.model

import java.time.Instant
import io.circe._
import io.circe.generic.semiauto._

object RemoteState {

  sealed trait Signal[S]
  case class Command[S](stateToSet: S) extends Signal[S]
  case class Event[S](stateChangedTo: S) extends Signal[S]

  object Signal {

    implicit def signalEncoder[S: Encoder]: Encoder[Signal[S]] =
      Encoder.forProduct1("signal") {
        case Command(stateToSet)   => ("Command", stateToSet)
        case Event(stateChangedTo) => ("Event", stateChangedTo)
      }

    implicit def signalDecoder[S: Decoder]: Decoder[Signal[S]] = (c: HCursor) =>
      c.downField("signal").as[String].flatMap {
        case "Command" => c.downField("stateToSet").as[S].map(Command(_))
        case "Event"   => c.downField("stateChangedTo").as[S].map(Event(_))
        case other =>
          Left(DecodingFailure(s"Unknown Signal type: $other", c.history))
      }
  }

  // Use semi-automatic derivation for the generic case class
  implicit def remoteStateEncoder[State: Encoder]: Encoder[RemoteState[State]] =
    deriveEncoder
  implicit def remoteStateDecoder[State: Decoder]: Decoder[RemoteState[State]] =
    deriveDecoder

}

case class RemoteState[State](
    confirmed: State,
    latestCommand: State,
    currentInconsistencyStart: Option[Instant]
)
