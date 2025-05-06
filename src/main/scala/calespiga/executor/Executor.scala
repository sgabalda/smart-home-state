package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.openhab.APIClient
import cats.effect.kernel.Resource
import cats.effect.{IO, ResourceIO}
import cats.implicits.catsSyntaxParallelTraverse1

sealed trait Executor {

  def execute(actions: Set[Action]): IO[List[ErrorManager.Error.ExecutionError]]

}

object Executor {

  final case class Impl(openHabApiClient: APIClient) extends Executor {

    private def processSingleAction(
        action: Action
    ): IO[Unit] = {
      action match {
        case Action.SetOpenHabItemValue(item, value) =>
          openHabApiClient.changeItem(item, value)
      }
    }

    override def execute(
        actions: Set[Action]
    ): IO[List[ErrorManager.Error.ExecutionError]] =
      actions.toList
        .parTraverse(action =>
          processSingleAction(action).attempt.map((_, action))
        )
        .map {
          _.collect { case (Left(throwable), action) =>
            ErrorManager.Error.ExecutionError(throwable, action)
          }
        }
  }

  def apply(openHabApiClient: APIClient): ResourceIO[Executor] =
    Resource.pure(Impl(openHabApiClient))
}
