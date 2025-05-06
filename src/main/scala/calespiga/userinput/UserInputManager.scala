package calespiga.userinput

import calespiga.ErrorManager
import calespiga.model.Event
import calespiga.openhab.APIClient
import cats.effect.IO
import fs2.Stream

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
  // TODO get the items from the configuration or annotations
  private val openHabItems: OpenHabItemsConverter =
    Map[String, String => Either[Throwable, Event.EventData]](
      "VentiladorBateriesManual" -> (v =>
        Right(
          Event.Temperature.Fans.BatteryFanSwitchManualChanged(
            "ON".equals(v)
          )
        )
      )
    )

  def apply(
      openhabApiClient: APIClient,
      itemsConverter: OpenHabItemsConverter = openHabItems
  ): UserInputManager = Impl(
    openhabApiClient,
    itemsConverter
  )

}
