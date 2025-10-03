package calespiga.model

object RemoteHeaterPowerState {

  sealed trait RemoteHeaterPowerStatus

  case object Off extends RemoteHeaterPowerStatus
  case object Power500 extends RemoteHeaterPowerStatus
  case object Power1000 extends RemoteHeaterPowerStatus
  case object Power2000 extends RemoteHeaterPowerStatus

  type RemoteHeaterPowerState = RemoteState[RemoteHeaterPowerStatus]

  def apply(
      confirmed: RemoteHeaterPowerStatus = Off,
      latestCommand: RemoteHeaterPowerStatus = Off,
      currentInconsistencyStart: Option[java.time.Instant] = None
  ): RemoteHeaterPowerState =
    RemoteState(confirmed, latestCommand, currentInconsistencyStart)

}
