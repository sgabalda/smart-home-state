package calespiga.ui

import calespiga.ErrorManager
import calespiga.model.Event
import cats.effect.IO
import fs2.Stream
import scala.concurrent.duration.*

object UserInterfaceManagerStub {
  def apply(
      userInputEventsStreamStub: () => Stream[
        IO,
        Either[ErrorManager.Error, Event.EventData]
      ] = () => Stream.empty,
      updateUIItemStub: (String, String) => IO[Unit] = (_, _) => IO.unit,
      sendNotificationStub: (String, String, Option[FiniteDuration]) => IO[
        Unit
      ] = (_, _, _) => IO.unit
  ): UserInterfaceManager = new UserInterfaceManager {
    override def userInputEventsStream
        : Stream[IO, Either[ErrorManager.Error, Event.EventData]] =
      userInputEventsStreamStub()
    override def updateUIItem(item: String, value: String): IO[Unit] =
      updateUIItemStub(item, value)
    override def sendNotification(
        id: String,
        message: String,
        repeatInterval: Option[FiniteDuration]
    ): IO[Unit] =
      sendNotificationStub(id, message, repeatInterval)
  }
}
