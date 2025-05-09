package calespiga.mqtt

import cats.effect.IO

object ProducerStub {

  def apply(
      publishStub: (String, Vector[Byte]) => IO[Unit] =
        (topic: String, payload: Vector[Byte]) => IO.unit
  ): Producer = (topic: String, payload: Vector[Byte]) =>
    publishStub(topic, payload)

}
