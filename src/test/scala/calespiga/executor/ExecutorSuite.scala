package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import scala.concurrent.duration._

class ExecutorSuite extends CatsEffectSuite {

  // Simple stubs for testing
  case class DirectExecutorStub(
      executeStub: Set[Action.Direct] => IO[
        List[ErrorManager.Error.ExecutionError]
      ] = _ => IO.pure(List.empty),
      onExecute: Set[Action.Direct] => IO[Unit] = _ => IO.unit
  ) extends DirectExecutor {
    override def execute(
        actions: Set[Action.Direct]
    ): IO[List[ErrorManager.Error.ExecutionError]] =
      onExecute(actions) *> executeStub(actions)
  }

  case class ScheduledExecutorStub(
      executeStub: Set[Action.Scheduled] => IO[
        List[ErrorManager.Error.ExecutionError]
      ] = _ => IO.pure(List.empty),
      onExecute: Set[Action.Scheduled] => IO[Unit] = _ => IO.unit
  ) extends ScheduledExecutor {
    override def execute(
        actions: Set[Action.Scheduled]
    ): IO[List[ErrorManager.Error.ExecutionError]] =
      onExecute(actions) *> executeStub(actions)
  }

  test("Executor delegates direct actions to DirectExecutor") {
    val directAction1 = Action.SetUIItemValue("item1", "value1")
    val directAction2 = Action.SendMqttStringMessage("topic", "message")
    val scheduledAction = Action.Delayed("id1", directAction1, 1.second)

    val actions = Set[Action](directAction1, directAction2, scheduledAction)

    for {
      receivedDirectActions <- Ref.of[IO, Set[Action.Direct]](Set.empty)
      receivedScheduledActions <- Ref.of[IO, Set[Action.Scheduled]](Set.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => receivedDirectActions.set(actions)
      )
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = actions => receivedScheduledActions.set(actions)
      )

      executor = Executor(directExecutor, scheduledExecutor)
      _ <- executor.execute(actions)

      actualDirectActions <- receivedDirectActions.get
      actualScheduledActions <- receivedScheduledActions.get
    } yield {
      assertEquals(actualDirectActions, Set(directAction1, directAction2))
      assertEquals(
        actualScheduledActions,
        Set[Action.Scheduled](scheduledAction)
      )
    }
  }

  test("Executor executes direct actions before scheduled actions") {
    val directAction = Action.SetUIItemValue("item", "value")
    val scheduledAction = Action.Delayed("id1", directAction, 1.second)

    val actions = Set[Action](directAction, scheduledAction)

    for {
      executionOrder <- Ref.of[IO, List[String]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = _ => executionOrder.update(_ :+ "direct")
      )
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = _ => executionOrder.update(_ :+ "scheduled")
      )

      executor = Executor(directExecutor, scheduledExecutor)
      _ <- executor.execute(actions)

      order <- executionOrder.get
    } yield {
      assertEquals(order, List("direct", "scheduled"))
    }
  }

  test("Executor handles both Delayed and Periodic scheduled actions") {
    val directAction = Action.SetUIItemValue("item", "value")
    val delayedAction = Action.Delayed("id1", directAction, 1.second)
    val periodicAction = Action.Periodic("id2", directAction, 5.seconds)

    val actions = Set[Action](directAction, delayedAction, periodicAction)

    for {
      receivedScheduledActions <- Ref.of[IO, Set[Action.Scheduled]](Set.empty)

      directExecutor = DirectExecutorStub()
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = actions => receivedScheduledActions.set(actions)
      )

      executor = Executor(directExecutor, scheduledExecutor)
      _ <- executor.execute(actions)

      actualScheduledActions <- receivedScheduledActions.get
    } yield {
      assertEquals(actualScheduledActions, Set(delayedAction, periodicAction))
    }
  }

  test("Executor merges errors from both executors") {
    val directAction = Action.SetUIItemValue("item", "value")
    val scheduledAction = Action.Delayed("id1", directAction, 1.second)

    val actions = Set[Action](directAction, scheduledAction)

    val directError = ErrorManager.Error.ExecutionError(
      new Exception("Direct error"),
      directAction
    )
    val scheduledError = ErrorManager.Error.ExecutionError(
      new Exception("Scheduled error"),
      scheduledAction
    )

    val directExecutor = DirectExecutorStub(
      executeStub = _ => IO.pure(List(directError))
    )
    val scheduledExecutor = ScheduledExecutorStub(
      executeStub = _ => IO.pure(List(scheduledError))
    )

    val executor = Executor(directExecutor, scheduledExecutor)

    for {
      errors <- executor.execute(actions)
    } yield {
      assertEquals(errors.length, 2)
      assert(errors.contains(directError))
      assert(errors.contains(scheduledError))
    }
  }

  test("Executor returns empty list when no errors occur") {
    val directAction = Action.SetUIItemValue("item", "value")
    val scheduledAction = Action.Delayed("id1", directAction, 1.second)

    val actions = Set[Action](directAction, scheduledAction)

    val directExecutor = DirectExecutorStub()
    val scheduledExecutor = ScheduledExecutorStub()

    val executor = Executor(directExecutor, scheduledExecutor)

    for {
      errors <- executor.execute(actions)
    } yield {
      assertEquals(errors, List.empty)
    }
  }

  test("Executor handles empty action set") {
    val actions = Set.empty[Action]

    for {
      receivedDirectActions <- Ref.of[IO, Set[Action.Direct]](Set.empty)
      receivedScheduledActions <- Ref.of[IO, Set[Action.Scheduled]](Set.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => receivedDirectActions.set(actions)
      )
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = actions => receivedScheduledActions.set(actions)
      )

      executor = Executor(directExecutor, scheduledExecutor)
      errors <- executor.execute(actions)

      actualDirectActions <- receivedDirectActions.get
      actualScheduledActions <- receivedScheduledActions.get
    } yield {
      assertEquals(actualDirectActions, Set.empty)
      assertEquals(actualScheduledActions, Set.empty)
      assertEquals(errors, List.empty)
    }
  }

  test("Executor handles only direct actions") {
    val directAction1 = Action.SetUIItemValue("item1", "value1")
    val directAction2 = Action.SendMqttStringMessage("topic", "message")

    val actions = Set[Action](directAction1, directAction2)

    for {
      receivedDirectActions <- Ref.of[IO, Set[Action.Direct]](Set.empty)
      receivedScheduledActions <- Ref.of[IO, Set[Action.Scheduled]](Set.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => receivedDirectActions.set(actions)
      )
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = actions => receivedScheduledActions.set(actions)
      )

      executor = Executor(directExecutor, scheduledExecutor)
      _ <- executor.execute(actions)

      actualDirectActions <- receivedDirectActions.get
      actualScheduledActions <- receivedScheduledActions.get
    } yield {
      assertEquals(actualDirectActions, Set(directAction1, directAction2))
      assertEquals(actualScheduledActions, Set.empty)
    }
  }

  test("Executor handles only scheduled actions") {
    val directAction = Action.SetUIItemValue("item", "value")
    val delayedAction = Action.Delayed("id1", directAction, 1.second)
    val periodicAction = Action.Periodic("id2", directAction, 5.seconds)

    val actions = Set[Action](delayedAction, periodicAction)

    for {
      receivedDirectActions <- Ref.of[IO, Set[Action.Direct]](Set.empty)
      receivedScheduledActions <- Ref.of[IO, Set[Action.Scheduled]](Set.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => receivedDirectActions.set(actions)
      )
      scheduledExecutor = ScheduledExecutorStub(
        onExecute = actions => receivedScheduledActions.set(actions)
      )

      executor = Executor(directExecutor, scheduledExecutor)
      _ <- executor.execute(actions)

      actualDirectActions <- receivedDirectActions.get
      actualScheduledActions <- receivedScheduledActions.get
    } yield {
      assertEquals(actualDirectActions, Set.empty)
      assertEquals(actualScheduledActions, Set(delayedAction, periodicAction))
    }
  }
}
