package calespiga.mqtt

import calespiga.config.MqttSourceConfig
import cats.effect.{IO, ResourceIO}
import com.comcast.ip4s.{Host, Port}
import fs2.Stream
import net.sigusr.mqtt.api.*
import net.sigusr.mqtt.api.QualityOfService.AtLeastOnce
import net.sigusr.mqtt.api.RetryConfig.Custom
import retry.RetryPolicies

import scala.concurrent.duration.{FiniteDuration, SECONDS}

trait Consumer {
  def startConsumer(): Stream[IO, Message]
}

object Consumer {

  private final case class Impl(session: Session[IO]) extends Consumer {
    override def startConsumer(): Stream[IO, Message] = session.messages
  }

  def apply(
      config: MqttSourceConfig,
      topics: Set[String]
  ): ResourceIO[Consumer] = {

    val subscribedTopics = topics.map((_, AtLeastOnce)).toVector

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
        traceMessages = config.traceMessages
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
