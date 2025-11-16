package calespiga.http

import cats.effect._
import org.http4s._
import calespiga.model.State
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.capabilities.fs2.Fs2Streams
import org.http4s.netty.server.NettyServerBuilder
import calespiga.config.HttpServerConfig
import calespiga.HealthStatusManager
import sttp.model.StatusCode

object Endpoints {
  val stateEndpoint: PublicEndpoint[Unit, String, State, Fs2Streams[IO]] =
    endpoint.get
      .in("state")
      .errorOut(stringBody)
      .out(jsonBody[State])

  val healthEndpoint: PublicEndpoint[Unit, String, String, Fs2Streams[IO]] =
    endpoint.get
      .in("health")
      .errorOut(statusCode(StatusCode.InternalServerError).and(stringBody))
      .out(stringBody)

  private def routes(
      ref: Ref[IO, Option[State]],
      healthStatusManager: HealthStatusManager
  ): HttpRoutes[IO] =

    val stateEndpointLogic = stateEndpoint.serverLogic { _ =>
      ref.get.map {
        case Some(state) => Right(state)
        case None        => Left("State not initialized")
      }
    }

    val healthEndpointLogic = healthEndpoint.serverLogic { _ =>
      healthStatusManager.status.map {
        case HealthStatusManager.Healthy =>
          Right("Healthy")
        case HealthStatusManager.Unhealthy(reason) =>
          Left(reason)
      }
    }

    Http4sServerInterpreter[IO]().toRoutes(
      List(stateEndpointLogic, healthEndpointLogic)
    )

  def apply(
      ref: Ref[IO, Option[State]],
      healthStatusManager: HealthStatusManager,
      httpServerConfig: HttpServerConfig
  ): Resource[IO, Unit] =
    NettyServerBuilder[IO]
      .withHttpApp(routes(ref, healthStatusManager).orNotFound)
      .bindHttp(httpServerConfig.port, httpServerConfig.host)
      .resource
      .map(_ => ())

}
