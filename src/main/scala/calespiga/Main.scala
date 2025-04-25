package calespiga

import calespiga.config.{ApplicationConfig, MqttSourceConfig}
import calespiga.mqtt.Consumer
import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {

  def run: IO[Unit] = {
    val appConfig = ApplicationConfig(
      MqttSourceConfig(
        host = "192.168.2.114",
        port = 1883,
        clientId = "test-client",
        topics = List("diposit1/temperature/batteries"),
        keepAlive = 10,
        cleanSession = true,
        traceMessages = false
      )
    )

    Consumer(appConfig.mqttSourceConfig).use { consumer =>
      consumer.startConsumer().compile.drain
    }
  }

}
