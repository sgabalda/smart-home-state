package calespiga.executor

import calespiga.model.Action
import calespiga.ErrorManager
import cats.effect.IO

trait Executor {
  def execute(actions: Set[Action]): IO[List[ErrorManager.Error.ExecutionError]]
}

object Executor {
  private final case class Impl(
      directExecutor: DirectExecutor,
      scheduledExecutor: ScheduledExecutor
  ) extends Executor {
    override def execute(
        actions: Set[Action]
    ): IO[List[ErrorManager.Error.ExecutionError]] = {
      val (direct, scheduled) = actions.toList.partitionMap {
        case d: Action.Direct     => Left(d)
        case dl: Action.Scheduled => Right(dl)
      }

      for {
        directErrors <- directExecutor.execute(direct.toSet)
        scheduledErrors <- scheduledExecutor.execute(scheduled.toSet)
      } yield directErrors ++ scheduledErrors
    }
  }

  def apply(
      directExecutor: DirectExecutor,
      scheduledExecutor: ScheduledExecutor
  ): Executor =
    Impl(directExecutor, scheduledExecutor)
}
