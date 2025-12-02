package calespiga.openhab

import calespiga.openhab.APIClient.ItemChangedEvent
import cats.effect.IO
import scala.concurrent.duration.*
import calespiga.config.OpenHabConfig

object ApiClientStub {

  val config = OpenHabConfig(
    host = "localhost",
    port = 8080,
    apiToken = "testToken",
    retryDelay = 5.seconds
  )

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
