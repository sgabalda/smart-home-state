package calespiga

import calespiga.config.ConfigLoader
import calespiga.mqtt.Consumer
import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    ConfigLoader.loadResource.flatMap { appConfig =>
      Consumer(appConfig.mqttSourceConfig)
    }.use { consumer =>
      consumer.startConsumer().compile.drain
    }

  }

}
