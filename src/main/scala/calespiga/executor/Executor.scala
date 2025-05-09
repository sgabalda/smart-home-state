package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.mqtt.ActionToMqttProducer
import calespiga.openhab.APIClient
import cats.effect.IO
import cats.implicits.catsSyntaxParallelTraverse1

sealed trait Executor {

  def execute(actions: Set[Action]): IO[List[ErrorManager.Error.ExecutionError]]

}

object Executor {

  final case class Impl(
      openHabApiClient: APIClient,
      mqttProducer: ActionToMqttProducer
  ) extends Executor {

    private def processSingleAction(
        action: Action
    ): IO[Unit] = {
      action match {
        case Action.SetOpenHabItemValue(item, value) =>
          openHabApiClient.changeItem(item, value)
        case a: Action.SendMqttStringMessage =>
          mqttProducer.actionToMqtt(a)
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

  def apply(
      openHabApiClient: APIClient,
      mqttProducer: ActionToMqttProducer
  ): Executor =
    Impl(openHabApiClient, mqttProducer)
}
