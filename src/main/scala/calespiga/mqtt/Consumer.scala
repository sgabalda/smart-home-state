package calespiga.mqtt

import calespiga.config.MqttConfig
import cats.effect.{IO, ResourceIO, Deferred}
import fs2.Stream
import net.sigusr.mqtt.api.*
import net.sigusr.mqtt.api.QualityOfService.AtLeastOnce
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import calespiga.HealthStatusManager.HealthComponentManager

trait Consumer {
  def startConsumer(): Stream[IO, Message]
}

object Consumer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private final case class Impl(sessionDeferred: Deferred[IO, Session[IO]])
      extends Consumer {
    override def startConsumer(): Stream[IO, Message] =
      Stream.eval(sessionDeferred.get).flatMap(_.messages)
  }

  def apply(
      config: MqttConfig,
      topics: Set[String],
      healthCheck: HealthComponentManager
  ): ResourceIO[Consumer] = {

    val subscribedTopics = topics.map((_, AtLeastOnce)).toVector
    for {
      sessionDeferred <- Deferred[IO, Session[IO]].toResource
      _ <- (logger.info("Starting MQTT Consumer session") *> SessionProvider(
        config,
        "_Consumer"
      ).use { session =>
        for {
          _ <- logger.info("MQTT Consumer session started")
          _ <- session.subscribe(subscribedTopics)
          _ <- sessionDeferred.complete(session)
          _ <- session.state.discrete
            .evalMap {
              case ConnectionState.SessionStarted =>
                healthCheck.setHealthy *> logger.info(
                  "MQTT Consumer set healthy"
                )
              case other =>
                healthCheck.setUnhealthy(other.toString) *> logger.error(
                  s"MQTT Consumer connection set unhealthy: $other"
                )
            }
            .compile
            .drain
        } yield ()
      }).background

    } yield Impl(sessionDeferred)

  }
}
