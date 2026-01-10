package calespiga.power.sunnyBoy

import cats.effect.{IO, Resource}
import calespiga.power.PowerProductionSource.*
import calespiga.config.SunnyBoyConfig
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.{Response, UriContext, WebSocketBackend, basicRequest}
import cats.effect.kernel.Ref
import calespiga.power.PowerProductionData
import java.net.{CookieManager, CookiePolicy}
import java.net.http.HttpClient

object SunnyBoyAPIClient {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(
      config: SunnyBoyConfig,
      backend: WebSocketBackend[IO],
      tokenHolder: Ref[IO, Option[String]],
      decoder: SunnyBoyDecoder
  ) extends PowerProductionOnRequestProvider {

    override def getCurrentPowerData: IO[PowerProductionData] =
      for {
        maybeToken <- tokenHolder.get
        token <- maybeToken match {
          case Some(token) =>
            logger.debug("Using existing SunnyBoy API token.").as(token)
          case None =>
            updateToken <* logger.debug("Obtained new SunnyBoy API token.")
        }
        data <- getData(token).orElse {
          logger.info(
            "SunnyBoy API token might be expired, updating token and retrying..."
          ) *>
            updateToken.flatMap(getData)
        }
      } yield data

    private val tokenUrl = uri"${config.loginUrl}"
    private val tokenBody =
      s"""{"right":"${config.username}","pass":"${config.password}"}"""
    private val dataUrl = uri"${config.dataUrl}"
    private val dataBody =
      s"""{"destDev":[],"keys":["${config.totalPowerCode}","${config.frequencyCode}","${config.linesCode}"]}"""

    private def getData(token: String): IO[PowerProductionData] =
      basicRequest
        .post(dataUrl.addParam("sid", token))
        .body(dataBody)
        .contentType("application/json")
        .send(backend)
        .flatMap {
          case Response(Right(successBody), code, _, _, _, _) =>
            decoder
              .getData(successBody)
              .flatMap(dp => decoder.toPowerProduction(dp).map((dp, _))) match {
              case Right((dp, producedPower)) =>
                logger.info(
                  s"Successfully decoded data: $dp => $producedPower"
                ) *> IO.pure(producedPower)
              case Left(decodingError) =>
                val message =
                  s"Failed to decode data response: ${decodingError.getMessage}, response: $successBody"
                logger.error(message) *> IO.raiseError(decodingError)
            }
          case Response(Left(error), code, _, _, _, _) =>
            val message = s"Failed to obtain data, error $code: $error"
            logger.error(message) *> IO.raiseError(Exception(message))
        }

    private def updateToken: IO[String] =
      basicRequest
        .post(tokenUrl)
        .body(tokenBody)
        .contentType("application/json")
        .send(backend)
        .flatMap {
          case Response(Right(successBody), code, _, _, _, _) =>
            decoder.getToken(successBody) match {
              case Right(sid) =>
                tokenHolder.set(Some(sid)) *>
                  logger
                    .debug(s"Token obtained successfully with code $code: $sid")
                    .as(sid)
              case Left(decodingError) =>
                val message =
                  s"Failed to decode token response: ${decodingError.getMessage}"
                logger.error(message) *> IO.raiseError(decodingError)
            }
          case Response(Left(error), code, _, _, _, _) =>
            val message =
              s"Failed to obtain token on request, error $code: $error"
            logger.error(message) *> IO.raiseError(Exception(message))
        }
  }

  private val defaultBackendResource = {
    val cookieManager = new CookieManager()
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)

    val httpClient =
      HttpClient
        .newBuilder()
        .cookieHandler(cookieManager)
        .build()

    HttpClientCatsBackend
      .resourceUsingClient[IO](httpClient)
  }

  def apply(
      config: SunnyBoyConfig,
      decoder: SunnyBoyDecoder,
      backendResource: Resource[IO, WebSocketBackend[IO]] =
        defaultBackendResource
  ): Resource[IO, PowerProductionOnRequestProvider] =
    for {
      backend <- backendResource
      tokenHolder <- Ref.of[IO, Option[String]](None).toResource
    } yield Impl(config, backend, tokenHolder, decoder)
}
