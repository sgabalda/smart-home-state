package calespiga.mqtt

import cats.effect.IO
import fs2.Stream
import net.sigusr.mqtt.api.Message

object ConsumerStub {
  def apply(startConsumerStub: () => Stream[IO, Message]): Consumer = () =>
    startConsumerStub()
}
