package calespiga.openhab

import calespiga.config.OpenHabConfig
import cats.effect.{IO, ResourceIO}
import cats.syntax.flatMap.*
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.{Backend, Response, UriContext, basicRequest}

trait APIClient{

  def changeItem(item: String, value: String): IO[ Unit]

}

object APIClient {

  final private case class Impl(
      webSocketBackend: Backend[IO],
      apiUrl: sttp.model.Uri
  ) extends APIClient {
    override def changeItem(item: String, value: String): IO[Unit] =
      basicRequest
        .post(apiUrl.addPath(item))
        .body(value)
        .send(webSocketBackend)
        .flatMap {
          case Response(Right(successBody), code, _, _, _, _) => IO.unit
          case Response(Left(error), code, _, _, _, _) =>
           IO.raiseError(
              new Exception(s"Failed to change item, error $code: $error")
            )
        }
  }
  
  def apply(config: OpenHabConfig,
            backendResource: ResourceIO[Backend[IO]] = HttpClientCatsBackend.resource[IO]()
           ): ResourceIO[APIClient] =
    backendResource.map {
      Impl(_, uri"http://${config.host}:${config.port}/rest/items")
    }
}
