package calespiga.mqtt

import cats.effect.IO

object ProducerStub {

  def apply(
      publishStub: (String, Vector[Byte]) => IO[Unit] =
        (_: String, _: Vector[Byte]) => IO.unit
  ): Producer = (topic: String, payload: Vector[Byte]) =>
    publishStub(topic, payload)

}
