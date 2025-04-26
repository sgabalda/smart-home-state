package calespiga

import calespiga.config.ConfigLoader
import calespiga.mqtt.Consumer
import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] = {
    (for {
      _ <- logger.info("Starting application...").toResource
      appConfig <- ConfigLoader.loadResource
      mqttConsumer <- Consumer(appConfig.mqttSourceConfig)
    } yield mqttConsumer).use { consumer =>
      consumer.startConsumer().compile.drain
    }

  }

}
