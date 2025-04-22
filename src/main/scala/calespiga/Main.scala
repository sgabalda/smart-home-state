package calespiga

import cats.effect.IOApp
import cats.effect.IO

object Main extends IOApp.Simple {

  // This is your new "main"!
  def run: IO[Unit] =
    IO.pure("bla").flatMap(IO.println)
}
