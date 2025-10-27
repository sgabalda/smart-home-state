package calespiga.config

import cats.effect.{IO, Resource, ResourceIO}
import pureconfig.*

object ConfigLoader {
  private def load: IO[ApplicationConfig] =
    ConfigSource.default.load[ApplicationConfig] match {
      case Right(config) => IO.pure(config)
      case Left(error)   =>
        IO.raiseError(new Exception(s"Failed to load config: $error"))
    }

  // Resource version for better resource management
  def loadResource: ResourceIO[ApplicationConfig] =
    Resource.eval(load)
}
