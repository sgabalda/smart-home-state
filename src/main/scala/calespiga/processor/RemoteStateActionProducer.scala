package calespiga.processor

import calespiga.model.{Action, RemoteState}
import java.time.Instant
import calespiga.model.Switch
import scala.concurrent.duration.FiniteDuration
import calespiga.model.Switch.Status

trait RemoteStateActionProducer[State] {
  def produceActionsForConfirmed(
      remoteState: RemoteState[State],
      now: Instant
  ): Set[Action]
  def produceActionsForCommand(
      remoteState: RemoteState[State],
      now: Instant
  ): Set[Action]
}

object RemoteStateActionProducer {

  type RemoteSwitchActionProducer = RemoteStateActionProducer[Switch.Status]

  // Synchronization status constants
  val SYNCHRONIZED = "Sincronitzat"
  val NOT_SYNCHRONIZED = "No sincronitzat"

  def apply(
      confirmedStateUIItem: String,
      mqttTopicForCommand: String,
      inconsistencyUIItem: String,
      id: String,
      resendInterval: FiniteDuration,
      timeoutInterval: FiniteDuration
  ): RemoteSwitchActionProducer =
    new RemoteStateActionProducer[Switch.Status] {
      private def addActionForInconsistencyStart(
          s: Option[Instant],
          now: Instant
      ): Set[Action] = {
        val setNotSynchronized =
          Action.SetOpenHabItemValue(inconsistencyUIItem, NOT_SYNCHRONIZED)
        val setSynchronized =
          Action.SetOpenHabItemValue(inconsistencyUIItem, SYNCHRONIZED)

        s match {
          case Some(timestamp) =>
            val timeOutInstant = timestamp.plus(
              java.time.Duration.ofMillis(timeoutInterval.toMillis)
            )
            if (now.isAfter(timeOutInstant))
              // the inconsistency started more than timeoutInterval ago, then set it as not synchronized and cancel the timeout
              Set(setNotSynchronized, Action.Cancel(id + "-timeout"))
            else
              // the inconsistency started less than timeoutInterval ago, then set it as synchronized and schedule the timeout
              import scala.jdk.DurationConverters._
              Set(
                setSynchronized,
                Action.Delayed(
                  id + "-timeout",
                  setNotSynchronized,
                  java.time.Duration.between(now, timeOutInstant).toScala
                )
              )
          case None =>
            Set(Action.Cancel(id + "-timeout"), setSynchronized)
        }
      }

      def produceActionsForCommand(
          remoteState: RemoteState[Status],
          now: Instant
      ): Set[Action] = {
        val action = Action.SendMqttStringMessage(
          mqttTopicForCommand,
          remoteState.latestCommand.toCommandString
        )
        Set(action, Action.Periodic(id + "-command", action, resendInterval)) ++
          addActionForInconsistencyStart(
            remoteState.currentInconsistencyStart,
            now
          )
      }

      def produceActionsForConfirmed(
          remoteState: RemoteState[Status],
          now: Instant
      ): Set[Action] = {
        Set(
          Action.SetOpenHabItemValue(
            confirmedStateUIItem,
            remoteState.confirmed.toStatusString
          )
        ) ++
          addActionForInconsistencyStart(
            remoteState.currentInconsistencyStart,
            now
          )
      }

    }
}
