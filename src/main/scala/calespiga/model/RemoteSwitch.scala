package calespiga.model

import Switch.*
import java.time.Instant

// remote switch state machine using ADTs
object RemoteSwitch {

  type RemoteSwitch = RemoteState[Status]

  def apply(
      confirmed: Status = Off,
      reported: Status = Off,
      currentInconsistencyStart: Option[Instant] = None
  ): RemoteSwitch =
    RemoteState(confirmed, reported, currentInconsistencyStart)

}
