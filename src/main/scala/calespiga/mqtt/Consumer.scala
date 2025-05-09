package calespiga.mqtt

import calespiga.config.MqttConfig
import cats.effect.{IO, ResourceIO}
import fs2.Stream
import net.sigusr.mqtt.api.*
import net.sigusr.mqtt.api.QualityOfService.AtLeastOnce

trait Consumer {
  def startConsumer(): Stream[IO, Message]
}

object Consumer {

  private final case class Impl(session: Session[IO]) extends Consumer {
    override def startConsumer(): Stream[IO, Message] = session.messages
  }

  def apply(
      config: MqttConfig,
      topics: Set[String]
  ): ResourceIO[Consumer] = {

    val subscribedTopics = topics.map((_, AtLeastOnce)).toVector
    SessionProvider(config, "_Consumer").flatMap { session =>
      session.subscribe(subscribedTopics).as(Impl(session)).toResource
    }

  }
}
