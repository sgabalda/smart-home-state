package calespiga

import cats.effect.IO

object ErrorManagerStub {
  def apply(
      onError: ErrorManager.Error => IO[Unit] = _ => IO.unit
  ): ErrorManager = { (error: ErrorManager.Error) =>
    onError(error)
  }
}
