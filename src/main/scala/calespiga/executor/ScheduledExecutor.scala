package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import cats.effect.{IO, Ref, Fiber, Resource}
import cats.implicits.{catsSyntaxParallelTraverse1}

trait ScheduledExecutor {

  def execute(
      actions: Set[Action.Scheduled]
  ): IO[List[ErrorManager.Error.ExecutionError]]

}

object ScheduledExecutor {

  final case class Impl(
      directExecutor: DirectExecutor,
      errorManager: ErrorManager,
      fibersRef: Ref[IO, Map[String, Fiber[IO, Throwable, Unit]]]
  ) extends ScheduledExecutor {

    private def processSingleScheduledAction(
        scheduledAction: Action.Scheduled
    ): IO[Unit] = {
      scheduledAction match {
        case delayed: Action.Delayed =>
          for {
            // Cancel existing fiber with the same ID if it exists
            _ <- fibersRef.get.flatMap { fibers =>
              fibers.get(delayed.id) match {
                case Some(existingFiber) =>
                  existingFiber.cancel *> fibersRef.update(_ - delayed.id)
                case None =>
                  IO.unit
              }
            }
            // Create new fiber for the delayed execution
            delayedExecution =
              for {
                _ <- IO.sleep(delayed.delay)
                errors <- directExecutor.execute(Set(delayed.action))
                _ <- errorManager.manageErrors(errors)
                // Remove the fiber from the map after execution
                _ <- fibersRef.update(_ - delayed.id)
              } yield ()
            newFiber <- delayedExecution.start

            // Store the new fiber in the map
            _ <- fibersRef.update(_ + (delayed.id -> newFiber))
          } yield ()

        case periodic: Action.Periodic =>
          // Define the recursive periodic execution function
          def periodicExecution: IO[Unit] =
            IO.sleep(periodic.period) *>
              directExecutor.execute(Set(periodic.action)).flatMap { errors =>
                errorManager.manageErrors(errors) *> periodicExecution
              }

          for {
            // Cancel existing fiber with the same ID if it exists
            _ <- fibersRef.get.flatMap { fibers =>
              fibers.get(periodic.id) match {
                case Some(existingFiber) =>
                  existingFiber.cancel *> fibersRef.update(_ - periodic.id)
                case None =>
                  IO.unit
              }
            }

            // Start the periodic execution fiber
            newFiber <- periodicExecution.start

            // Store the new fiber in the map
            _ <- fibersRef.update(_ + (periodic.id -> newFiber))
          } yield ()

        case cancel: Action.Cancel =>
          // Cancel existing fiber with the specified ID if it exists
          fibersRef.get.flatMap { fibers =>
            fibers.get(cancel.id) match {
              case Some(existingFiber) =>
                existingFiber.cancel *> fibersRef.update(_ - cancel.id)
              case None =>
                IO.unit
            }
          }
      }
    }

    override def execute(
        actions: Set[Action.Scheduled]
    ): IO[List[ErrorManager.Error.ExecutionError]] =
      actions.toList
        .parTraverse(action =>
          processSingleScheduledAction(action).attempt.map((_, action))
        )
        .map {
          _.collect { case (Left(throwable), action) =>
            ErrorManager.Error.ExecutionError(throwable, action)
          }
        }
  }

  def apply(
      directExecutor: DirectExecutor,
      errorManager: ErrorManager
  ): Resource[IO, ScheduledExecutor] =
    Resource
      .eval(Ref.of[IO, Map[String, Fiber[IO, Throwable, Unit]]](Map.empty))
      .flatMap { fibersRef =>
        val scheduler = Impl(directExecutor, errorManager, fibersRef)
        Resource.make(IO.pure(scheduler)) { _ =>
          // Cancel all running fibers when the resource is released
          fibersRef.get.flatMap { fibers =>
            fibers.values.toList.parTraverse(_.cancel).void
          }
        }
      }
}
