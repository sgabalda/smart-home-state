package calespiga.openhab

import calespiga.config.OpenHabConfig
import calespiga.openhab.APIClient.ItemChangedEvent
import cats.effect.{IO, ResourceIO}
import cats.syntax.flatMap.*
import fs2.Stream
import io.circe.generic.auto.*
import io.circe.parser.decode
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.ws.async.asWebSocketUnsafe
import sttp.client4.{Response, UriContext, WebSocketBackend, basicRequest}
import sttp.ws.WebSocket

import scala.concurrent.duration.*
import scala.language.postfixOps

trait APIClient {

  def changeItem(item: String, value: String): IO[Unit]

  def itemChanges(
      items: Set[String]
  ): Stream[IO, Either[Throwable, ItemChangedEvent]]
}

object APIClient {

  case class WSEvent(`type`: String, topic: String, payload: String)
  case class ItemChangedEvent(item: String, value: String)
  object ItemChangedEvent {

    private case class ItemChangedEventPayload(value: String)

    def apply(event: WSEvent): Either[Throwable, ItemChangedEvent] = for {
      value <- decode[ItemChangedEventPayload](event.payload).map(_.value)
      item <- event.topic.split("/") match {
        case Array("openhab", "items", item, "statechanged") => Right(item)
        case _ =>
          Left(
            new Exception(
              s"Invalid topic for ItemStateChangedEvent: ${event.topic}"
            )
          )
      }
    } yield { ItemChangedEvent(item, value) }
  }

  private val pingMessage = """
      |{
      |    "type": "WebSocketEvent",
      |    "topic": "openhab/websocket/heartbeat",
      |    "payload": "PING",
      |    "source": "SmartHomeState"
      |}""".stripMargin

  private val filterMessage = """
      |{
      |    "type": "WebSocketEvent",
      |    "topic": "openhab/websocket/filter/type",
      |    "payload": "[\"ItemStateChangedEvent\"]",
      |    "source": "SmartHomeState"
      |}""".stripMargin

  final private case class Impl(
      webSocketBackend: WebSocketBackend[IO],
      openHabConfig: OpenHabConfig
  ) extends APIClient {

    private val apiUrl =
      uri"http://${openHabConfig.host}:${openHabConfig.port}/rest/items"
    private val wsUrl =
      uri"ws://${openHabConfig.host}:${openHabConfig.port}/ws?accessToken=${openHabConfig.apiToken}"

    private def openWebSocket(): IO[WebSocket[IO]] = {
      basicRequest
        .get(wsUrl)
        .response(asWebSocketUnsafe[IO])
        .send(webSocketBackend)
        .flatMap {
          case Response(Right(webSocket), code, _, _, _, _) =>
            IO.pure(webSocket)
          case Response(Left(error), code, text, headers, _, _) =>
            IO.raiseError(
              Exception(
                s"Failed to open WebSocket, error $code ($text): $error"
              )
            )
        }
    }

    override def itemChanges(
        items: Set[String]
    ): Stream[IO, Either[Throwable, ItemChangedEvent]] = {
      for {
        ws <- Stream.eval(openWebSocket())
        _ <- Stream.eval(ws.sendText(filterMessage))
        ping = Stream
          .awakeDelay[IO](5 seconds)
          .evalTap(_ => ws.sendText(pingMessage))
        result <- Stream
          .repeatEval(ws.receiveText())
          .evalMapFilter { frame =>
            decode[WSEvent](frame) match {
              case Left(error) =>
                IO.pure(Some(Left(error)))
              case Right(value) =>
                value.`type` match {
                  case "ItemStateChangedEvent" =>
                    ItemChangedEvent(value) match {
                      case error @ Left(_) => IO.pure(Some(error))
                      case Right(itemChangedEvent)
                          if items.contains(itemChangedEvent.item) =>
                        IO.pure(Some(Right(itemChangedEvent)))
                      case _ => IO.pure(None)
                    }
                  case _ => IO.pure(None)
                }
            }
          }
          .concurrently(ping)
      } yield {
        result
      }
    }

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

  def apply(
      config: OpenHabConfig,
      backendResource: ResourceIO[WebSocketBackend[IO]] =
        HttpClientCatsBackend.resource[IO]()
  ): ResourceIO[APIClient] =
    backendResource.map { Impl(_, config) }
}
