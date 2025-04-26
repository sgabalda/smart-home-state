package calespiga.mqtt

import calespiga.config.MqttSourceConfig
import calespiga.model.Event
import calespiga.model.event.TemperatureRelated
import cats.effect.{IO, ResourceIO}
import com.comcast.ip4s.{Host, Port}
import fs2.Stream
import net.sigusr.mqtt.api.QualityOfService.AtLeastOnce
import net.sigusr.mqtt.api.RetryConfig.Custom
import net.sigusr.mqtt.api.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.RetryPolicies

import scala.concurrent.duration.{FiniteDuration, SECONDS}

trait Consumer {
  def startConsumer(): Stream[IO, Either[Throwable, Event]]
}

object Consumer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(session: Session[IO]) extends Consumer {
    override def startConsumer(): Stream[IO, Either[Throwable, Event]] = {
      session.messages.evalMap { case Message(topic, payload) =>
        logger.info(s"Received message on topic $topic: ${payload.toString}") *>
          IO.realTimeInstant.map { timestamp =>
            Right(
              Event.Temperature(
                timestamp,
                TemperatureRelated.BatteryTemperatureMeasured(5.0)
              )
            )
          }
      }
    }
  }

  def apply(
      config: MqttSourceConfig
  ): ResourceIO[Consumer] = {

    val subscribedTopics = config.topics.map((_, AtLeastOnce)).toVector

    val retryConfig: Custom[IO] = Custom[IO](
      RetryPolicies
        .limitRetries[IO](5)
        .join(RetryPolicies.fullJitter[IO](FiniteDuration(2, SECONDS)))
    )
    val transportConfig =
      TransportConfig[IO](
        Host.fromString(config.host).get,
        Port.fromString(config.port.toString).get,
        // TLS support looks like
        // 8883,
        // tlsConfig = Some(TLSConfig(TLSContextKind.System)),
        retryConfig = retryConfig,
        traceMessages = true
      )
    val sessionConfig =
      SessionConfig(
        config.clientId,
        cleanSession = config.cleanSession,
        keepAlive = config.keepAlive
      )

    Session[IO](transportConfig, sessionConfig).flatMap { session =>
      session.subscribe(subscribedTopics).as(Impl(session)).toResource
    }

  }
}
