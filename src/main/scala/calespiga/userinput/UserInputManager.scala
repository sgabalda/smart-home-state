package calespiga.userinput

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.openhab.APIClient
import cats.effect.IO
import fs2.Stream
import scala.util.Try

trait UserInputManager {

  def userInputEventsStream(): Stream[IO, Either[ErrorManager.Error, Event]]

}

object UserInputManager {

  private final case class Impl(
      openhabApiClient: APIClient,
      itemsConverter: OpenHabItemsConverter
  ) extends UserInputManager {
    override def userInputEventsStream()
        : Stream[IO, Either[ErrorManager.Error, Event]] = {

      openhabApiClient
        .itemChanges(itemsConverter.keySet)
        .evalMap {
          case Left(value) =>
            IO.pure(Left(ErrorManager.Error.OpenHabInputError(value)))
          case Right(itemValue) =>
            itemsConverter
              .get(itemValue.item) match {
              case None =>
                IO.pure(
                  Left(
                    ErrorManager.Error.OpenHabInputError(
                      new Exception(
                        s"Item not found for conversion, but registered: $itemValue"
                      )
                    )
                  )
                )
              case Some(f) =>
                f(itemValue.value) match {
                  case Left(value) =>
                    IO.pure(Left(ErrorManager.Error.OpenHabInputError(value)))
                  case Right(eventData) =>
                    IO.realTimeInstant.map(ts => Right(Event(ts, eventData)))
                }

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
      itemsConverter: OpenHabItemsConverter = openHabItems
  ): UserInputManager = Impl(
    openhabApiClient,
    itemsConverter
  )

}
