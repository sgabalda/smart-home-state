package calespiga.ui

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.openhab.APIClient
import cats.effect.IO
import fs2.Stream
import scala.util.Try
import calespiga.config.UIConfig
import cats.effect.Ref
import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import cats.implicits.catsSyntaxApplicativeByName

trait UserInterfaceManager {

  def userInputEventsStream
      : Stream[IO, Either[ErrorManager.Error, Event.EventData]]
  def updateUIItem(item: String, value: String): IO[Unit]
  def sendNotification(
      id: String,
      message: String,
      repeatInterval: Option[FiniteDuration]
  ): IO[Unit]
}

object UserInterfaceManager {

  private final case class Impl(
      openhabApiClient: APIClient,
      itemsConverter: OpenHabItemsConverter,
      uiConfig: UIConfig,
      sentMessages: Ref[IO, Map[String, Instant]]
  ) extends UserInterfaceManager {

    override def updateUIItem(item: String, value: String): IO[Unit] =
      openhabApiClient.changeItem(item, value)

    override def sendNotification(
        id: String,
        message: String,
        repeatInterval: Option[FiniteDuration]
    ): IO[Unit] =
      for {
        now <- IO.realTimeInstant
        repeatIntervalValue = repeatInterval.getOrElse(
          uiConfig.defaultRepeatInterval
        )
        shouldSend <- sentMessages.modify { sent =>
          sent.get(id) match {
            case None =>
              (sent + (id -> now), true)
            case Some(lastSent) =>
              if (
                now.isAfter(lastSent.plusMillis(repeatIntervalValue.toMillis))
              ) {
                (sent + (id -> now), true)
              } else {
                (sent, false)
              }
          }
        }
        _ <- openhabApiClient
          .changeItem(uiConfig.notificationsItem, message)
          .whenA(shouldSend)
      } yield ()

    override def userInputEventsStream
        : Stream[IO, Either[ErrorManager.Error, Event.EventData]] = {

      openhabApiClient
        .itemChanges(itemsConverter.keySet)
        .map[Either[ErrorManager.Error, Event.EventData]] {
          case Left(value) =>
            Left(ErrorManager.Error.OpenHabInputError(value))
          case Right(itemValue) =>
            itemsConverter
              .get(itemValue.item) match {
              case None =>
                Left(
                  ErrorManager.Error.OpenHabInputError(
                    new Exception(
                      s"Item not found for conversion, but registered: $itemValue"
                    )
                  )
                )
              case Some(f) =>
                f(itemValue.value).fold(
                  value => Left(ErrorManager.Error.OpenHabInputError(value)),
                  r => Right(r)
                )

            }
        }
    }
  }

  type OpenHabItemsConverter =
    Map[String, String => Either[Throwable, Event.EventData]]

  private val openHabItems: OpenHabItemsConverter =
    Event.eventsOpenHabInputItemsConverter.map {
      case (itemName, constructorOfString) =>
        val conversion: String => Either[Throwable, Event.EventData] =
          i => Try(constructorOfString(i)).toEither
        (itemName, conversion)
    }.toMap

  def apply(
      openhabApiClient: APIClient,
      uiConfig: UIConfig,
      itemsConverter: OpenHabItemsConverter = openHabItems
  ): IO[UserInterfaceManager] =
    Ref.of[IO, Map[String, Instant]](Map.empty).map { sentMessages =>
      Impl(openhabApiClient, itemsConverter, uiConfig, sentMessages)
    }

}
