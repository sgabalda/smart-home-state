package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.mqtt.ActionToMqttProducer
import cats.effect.IO
import cats.implicits.catsSyntaxParallelTraverse1
import calespiga.ui.UserInterfaceManager

trait DirectExecutor {

  def execute(
      actions: Set[Action.Direct]
  ): IO[List[ErrorManager.Error.ExecutionError]]

}

object DirectExecutor {

  final case class Impl(
      uiManager: UserInterfaceManager,
      mqttProducer: ActionToMqttProducer
  ) extends DirectExecutor {

    private def processSingleAction(
        action: Action.Direct
    ): IO[Unit] = {
      action match {
        case Action.SetUIItemValue(item, value) =>
          uiManager.updateUIItem(item, value)
        case a: Action.SendMqttStringMessage =>
          mqttProducer.actionToMqtt(a)
        case Action.SendNotification(id, message, repeatInterval) =>
          uiManager.sendNotification(id, message, repeatInterval)
      }
    }

    override def execute(
        actions: Set[Action.Direct]
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
      uiManager: UserInterfaceManager,
      mqttProducer: ActionToMqttProducer
  ): DirectExecutor =
    Impl(uiManager, mqttProducer)
}
