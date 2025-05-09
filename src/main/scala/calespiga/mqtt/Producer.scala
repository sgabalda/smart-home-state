package calespiga.mqtt

import calespiga.config.MqttConfig
import cats.effect.{IO, ResourceIO}
import net.sigusr.mqtt.api.Session

trait Producer {

  def publish(topic: String, payload: Vector[Byte]): IO[Unit]

}

object Producer {

  final private case class Impl(session: Session[IO]) extends Producer {
    override def publish(topic: String, payload: Vector[Byte]): IO[Unit] =
      session.publish(topic, payload)
  }

  def apply(config: MqttConfig): ResourceIO[Producer] =
    SessionProvider(config, "_Producer").map(Impl(_))
}
