package calespiga.processor.utils

import calespiga.model.RemoteState
import calespiga.model.Action

trait RemoteStateActionManager[State] {

  /** To be called when the remote device has to be turned to a new state
    *
    * @param commandToSet
    *   the state that wants to be set
    * @param currentState
    *   the current RemoteState of the device
    * @return
    *   a tuple with the set of actions to perform and the new state to store
    */
  def turnRemote(
      commandToSet: State,
      currentState: RemoteState[State]
  ): (Set[Action], RemoteState[State])
}

object RemoteStateActionManager {

// Synchronization status constants
  val SYNCHRONIZED = "Sincronitzat"
  val NOT_SYNCHRONIZED = "No sincronitzat"

  val COMMAND_ACTION_SUFFIX = "-command"
  val INCONSISTENCY_TIMEOUT_ACTION_SUFFIX = "-inconsistency-timeout"

  private case class Impl[State](
      id: String,
      resendInterval: scala.concurrent.duration.FiniteDuration,
      timeoutInterval: scala.concurrent.duration.FiniteDuration,
      mqttTopicForCommand: String,
      inconsistencyUIItem: String
  ) extends RemoteStateActionManager[State] {

    private object Actions {
      private val setNotSynchronized =
        Action.SetOpenHabItemValue(inconsistencyUIItem, NOT_SYNCHRONIZED)
      private val setSynchronized =
        Action.SetOpenHabItemValue(inconsistencyUIItem, SYNCHRONIZED)
      private def commandAction(command: State) =
        Action.SendMqttStringMessage(
          mqttTopicForCommand,
          command.toString
        )
      def periodicCommandAction(command: State) = {
        Action.Periodic(
          id + COMMAND_ACTION_SUFFIX,
          commandAction(command),
          resendInterval
        )
      }
      def commandActionWithResend(command: State) = {
        Set(commandAction(command), periodicCommandAction(command))
      }
      private val delayedSetNotSynchronized = Action.Delayed(
        id + INCONSISTENCY_TIMEOUT_ACTION_SUFFIX,
        setNotSynchronized,
        timeoutInterval
      )
      val synchronizedWithDelayedInconsistency = Set(
        setSynchronized,
        delayedSetNotSynchronized
      )
      val cancelInconsistency =
        Set(
          Action.Cancel(id + INCONSISTENCY_TIMEOUT_ACTION_SUFFIX),
          setSynchronized
        )
    }

    /** To be called when the remote device has to be turned to a new state
      *
      * @param commandToSet
      *   the state that wants to be set
      * @param currentState
      *   the current RemoteState of the device
      * @return
      *   a tuple with the set of actions to perform and the new state to store
      */
    def turnRemote(
        commandToSet: State,
        currentState: RemoteState[State]
    ): (Set[Action], RemoteState[State]) = {
      val commandInSync = commandToSet == currentState.latestCommand
      val stateInSync = commandToSet == currentState.confirmed
      (commandInSync, stateInSync) match
        case (false, false) =>
          (
            Actions.synchronizedWithDelayedInconsistency ++ Actions
              .commandActionWithResend(commandToSet),
            currentState.copy(latestCommand = commandToSet)
          )
        case (false, true) =>
          // as the command is not the latest command, the resend has to be updated, but the timeout can be cancelled
          (
            Actions.cancelInconsistency ++ Actions.commandActionWithResend(
              commandToSet
            ),
            currentState.copy(latestCommand = commandToSet)
          )
        case (true, false) =>
          // the command is already the latest command, the resend was already there, so not change anything
          (Set.empty, currentState)
        case (true, true) =>
          // everything is in sync, the resend action is already there, so nothing to do
          (Set.empty, currentState)
    }

  }
  def apply[State](
      id: String,
      resendInterval: scala.concurrent.duration.FiniteDuration,
      timeoutInterval: scala.concurrent.duration.FiniteDuration,
      mqttTopicForCommand: String,
      inconsistencyUIItem: String
  ): RemoteStateActionManager[State] =
    Impl(
      id,
      resendInterval,
      timeoutInterval,
      mqttTopicForCommand,
      inconsistencyUIItem
    )

}
