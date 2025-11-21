package calespiga.mqtt

import calespiga.config.MqttConfig
import cats.effect.{IO, ResourceIO, Deferred}
import net.sigusr.mqtt.api.Session
import net.sigusr.mqtt.api.ConnectionState
import calespiga.HealthStatusManager.HealthComponentManager
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Producer {

  def publish(topic: String, payload: Vector[Byte]): IO[Unit]

}

object Producer {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  final private case class Impl(sessionDeferred: Deferred[IO, Session[IO]])
      extends Producer {
    override def publish(topic: String, payload: Vector[Byte]): IO[Unit] =
      sessionDeferred.get.flatMap(_.publish(topic, payload))
  }

  def apply(
      config: MqttConfig,
      healthCheck: HealthComponentManager
  ): ResourceIO[Producer] =
    for {
      sessionDeferred <- Deferred[IO, Session[IO]].toResource
      impl = Impl(sessionDeferred)
      _ <- (logger.info("Starting MQTT Producer session") *> SessionProvider(
        config,
        "_Producer"
      ).use { session =>
        for {
          _ <- logger.info("MQTT Producer session started")
          _ <- sessionDeferred.complete(session)
          _ <- session.state.discrete
            .evalMap {
              case ConnectionState.SessionStarted =>
                healthCheck.setHealthy *> logger.info(
                  "MQTT Producer set healthy"
                )
              case other =>
                healthCheck.setUnhealthy(other.toString) *>
                  logger.error("MQTT Producer set unhealthy: " + other.toString)
            }
            .compile
            .drain
        } yield ()
      }).background
    } yield impl
}
