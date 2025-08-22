package calespiga.processor

import calespiga.model.{Action, RemoteState}
import java.time.Instant
import calespiga.model.Switch

sealed trait RemoteStateActionProducer[State] {
  def produceActionsFor(remoteState: RemoteState[State]): Set[Action]
}

object RemoteStateActionProducer {

  type RemoteSwitchActionProducer = RemoteStateActionProducer[Switch.Status]

  def forSwitchWithUIItems(
      confirmedStateUIItem: String,
      mqttTopicForCommand: String
  ): RemoteSwitchActionProducer =
    RemoteStateActionProducer[Switch.Status](
      actionsForConfirmedState = s =>
        Set(Action.SetOpenHabItemValue(confirmedStateUIItem, s.toStatusString)),
      actionsForCommand = s =>
        Set(
          Action.SendMqttStringMessage(mqttTopicForCommand, s.toCommandString)
        ), // TODO schedule a resend of the command instead of only sending it
      actionsForInconsistencyStart =
        _ => Set.empty // TODO schedule a timeout (or remove it)
    )

  def apply[State](
      actionsForConfirmedState: State => Set[Action],
      actionsForCommand: State => Set[Action],
      actionsForInconsistencyStart: Option[Instant] => Set[Action]
  ): RemoteStateActionProducer[State] = new RemoteStateActionProducer[State] {
    def produceActionsFor(remoteState: RemoteState[State]): Set[Action] =
      actionsForConfirmedState(remoteState.confirmed) ++ actionsForCommand(
        remoteState.latestCommand
      )
        ++ actionsForInconsistencyStart(remoteState.currentInconsistencyStart)
  }
}
