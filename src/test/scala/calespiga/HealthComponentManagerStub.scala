package calespiga

import cats.effect.IO

object HealthComponentManagerStub {
  def apply(
      onHealthy: IO[Unit] = IO.unit,
      onUnhealthy: IO[Unit] = IO.unit
  ): HealthStatusManager.HealthComponentManager =
    new HealthStatusManager.HealthComponentManager {
      override def setHealthy: IO[Unit] = onHealthy
      override def setUnhealthy(reason: String): IO[Unit] = onUnhealthy
    }
}
