package calespiga

import cats.effect.IO
import cats.effect.IOApp

object Main extends IOApp.Simple {
  override def run: IO[Unit] =
    AppResources.resource(DefaultExternalInterfaces).use(AppRuntime.run)
}
