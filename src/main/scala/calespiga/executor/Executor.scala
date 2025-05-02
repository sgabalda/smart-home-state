package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import cats.effect.kernel.Resource
import cats.effect.{IO, ResourceIO}
import cats.implicits.toFoldableOps
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Executor {

  def execute(actions: Set[Action]): IO[List[ErrorManager.Error.ExecutionError]]

}

object Executor {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  final case class Impl() extends Executor {
    override def execute(
        actions: Set[Action]
    ): IO[List[ErrorManager.Error.ExecutionError]] =
      actions.toList.foldM(List.empty)((prevErrors, action) =>
        logger.info(s"Would be executing the action $action").as(prevErrors)
      )
  }

  def apply(): ResourceIO[Executor] = Resource.pure(Impl())
}
