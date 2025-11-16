package calespiga

import cats.effect.IO
import cats.effect.Resource
import calespiga.HealthStatusManager.HealthComponentManager

trait HealthStatusManager {
  def status: IO[HealthStatusManager.Health]
  def setHealthy(component: HealthStatusManager.Component): IO[Unit]
  def setUnhealthy(
      component: HealthStatusManager.Component,
      reason: String
  ): IO[Unit]

  def componentHealthManager(
      component: HealthStatusManager.Component
  ): HealthComponentManager =
    new HealthComponentManager {
      override def setHealthy: IO[Unit] =
        HealthStatusManager.this.setHealthy(component)

      override def setUnhealthy(reason: String): IO[Unit] =
        HealthStatusManager.this.setUnhealthy(component, reason)
    }
}

object HealthStatusManager {

  trait HealthComponentManager {
    def setHealthy: IO[Unit]
    def setUnhealthy(reason: String): IO[Unit]
  }

  sealed trait Health
  case object Healthy extends Health
  case class Unhealthy(reason: String) extends Health

  enum Component {
    case MqttConsumer
    case MqttProducer
    case OpenHabApiClient
    case StatePersistence
  }

  private case class Impl(
      healthStatusRef: cats.effect.Ref[IO, Map[Component, Health]]
  ) extends HealthStatusManager {

    override def status: IO[Health] =
      healthStatusRef.get.map { statusMap =>
        val unhealthyReasons = statusMap.collect {
          case (component, Unhealthy(reason)) =>
            s"${component.toString}: $reason"
        }
        if (unhealthyReasons.isEmpty) Healthy
        else Unhealthy(unhealthyReasons.mkString("\n"))
      }

    override def setHealthy(component: Component): IO[Unit] =
      healthStatusRef.update(_.updated(component, Healthy))

    override def setUnhealthy(
        component: Component,
        reason: String
    ): IO[Unit] =
      healthStatusRef.update(
        _.updated(component, Unhealthy(reason))
      )
  }

  def apply(): Resource[IO, HealthStatusManager] =
    for {
      ref <- cats.effect.Ref
        .of[IO, Map[Component, Health]](
          Component.values.map(_ -> Unhealthy("Not initialized")).toMap
        )
        .toResource
    } yield Impl(ref)

}
