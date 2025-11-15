package calespiga.http

import cats.effect.{IO, Resource}
import cats.effect._
import org.http4s._
import calespiga.model.State
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import org.http4s.HttpRoutes
import org.http4s.netty.server.NettyServerBuilder
import calespiga.config.HttpServerConfig

object Endpoints {

  val stateEndpoint: PublicEndpoint[Unit, String, State, Any] =
    endpoint.get
      .in("state")
      .errorOut(stringBody)
      .out(jsonBody[State])

  private def routes(ref: Ref[IO, Option[State]]): HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      stateEndpoint.serverLogic { _ =>
        ref.get.map {
          case Some(state) => Right(state)
          case None        => Left("State not initialized")
        }
      }
    )

  def server(
      ref: Ref[IO, Option[State]],
      httpServerConfig: HttpServerConfig
  ): Resource[IO, Unit] =
    NettyServerBuilder[IO]
      .withHttpApp(routes(ref).orNotFound)
      .bindHttp(httpServerConfig.port, httpServerConfig.host)
      .resource
      .map(_ => ())

}
