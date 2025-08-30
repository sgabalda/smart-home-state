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
        val setOffline =
          Action.SetOpenHabItemValue(inconsistencyUIItem, "Offline")
        val setOnline =
          Action.SetOpenHabItemValue(inconsistencyUIItem, "Online")

        s match {
          case Some(timestamp) =>
            val timeOutInstant = timestamp.plus(
              java.time.Duration.ofMillis(timeoutInterval.toMillis)
            )
            if (now.isAfter(timeOutInstant))
              // the inconsistency started more than timeoutInterval ago, then set it as offline and cancel the timeout
              Set(setOffline, Action.Cancel(id + "-timeout"))
            else
              // the inconsistency started less than timeoutInterval ago, then set it as online and schedule the timeout
              import scala.jdk.DurationConverters._
              Set(
                setOnline,
                Action.Delayed(
                  id + "-timeout",
                  setOffline,
                  java.time.Duration.between(now, timeOutInstant).toScala
                )
              )
          case None =>
            Set(Action.Cancel(id + "-timeout"), setOnline)
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
