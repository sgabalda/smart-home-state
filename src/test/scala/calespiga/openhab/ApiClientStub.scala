package calespiga.openhab

import calespiga.openhab.APIClient.ItemChangedEvent
import cats.effect.IO

object ApiClientStub {

  def apply(
      changeItemStub: (String, String) => IO[Unit] = (_, _) => IO.unit,
      itemChangesStub: Set[String] => fs2.Stream[IO, Either[
        Throwable,
        ItemChangedEvent
      ]] = _ => fs2.Stream.empty
  ): APIClient = new APIClient {
    override def changeItem(item: String, value: String): IO[Unit] =
      changeItemStub(item, value)
    override def itemChanges(
        items: Set[String]
    ): fs2.Stream[IO, Either[Throwable, ItemChangedEvent]] = itemChangesStub(
      items
    )

  }
}
