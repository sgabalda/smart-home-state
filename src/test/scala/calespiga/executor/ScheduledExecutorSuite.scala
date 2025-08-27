package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import cats.effect.{IO, Ref}
import cats.effect.testkit.TestControl
import munit.CatsEffectSuite
import scala.concurrent.duration._

class ScheduledExecutorSuite extends CatsEffectSuite {

  // Stub for DirectExecutor
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

  // Stub for ErrorManager
  case class ErrorManagerStub(
      manageErrorStub: ErrorManager.Error => IO[Unit] = _ => IO.unit,
      onManageError: ErrorManager.Error => IO[Unit] = _ => IO.unit
  ) extends ErrorManager {
    override def manageError(error: ErrorManager.Error): IO[Unit] =
      onManageError(error) *> manageErrorStub(error)
  }

  test("ScheduledExecutor executes delayed action after specified delay") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val delayedAction = Action.Delayed("delayed1", directAction, 100.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      // Execute the delayed action
      _ <- scheduledExecutor.execute(Set(delayedAction))

      // Check that action is not executed immediately
      actionsBeforeDelay <- executedActions.get

      // Advance time by 50ms - should not be executed yet
      _ <- IO.sleep(50.millis)
      actionsAfter50ms <- executedActions.get

      // Advance time by remaining 50ms to complete the delay
      _ <- IO.sleep(50.millis)

      // Give the fiber a chance to complete
      _ <- IO.sleep(1.millis)

      actionsAfterDelay <- executedActions.get
    } yield {
      assertEquals(actionsBeforeDelay, List.empty)
      assertEquals(actionsAfter50ms, List.empty)
      assertEquals(actionsAfterDelay, List(Set(directAction)))
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor cancels previous delayed action with same ID") {
    val directAction1 = Action.SetOpenHabItemValue("item1", "value1")
    val directAction2 = Action.SetOpenHabItemValue("item2", "value2")
    val delayedAction1 = Action.Delayed("same-id", directAction1, 200.millis)
    val delayedAction2 = Action.Delayed("same-id", directAction2, 100.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      // Execute first delayed action
      _ <- scheduledExecutor.execute(Set(delayedAction1))

      // Advance time by 50ms, then schedule second action with same ID
      _ <- IO.sleep(50.millis)
      _ <- scheduledExecutor.execute(Set(delayedAction2))

      // Advance time by 100ms to complete the second action (150ms total from start)
      _ <- IO.sleep(100.millis)
      executedActionsAfterSecond <- executedActions.get

      // Advance time by additional 100ms to ensure first action would have executed if not cancelled (250ms total)
      _ <- IO.sleep(100.millis)
      finalExecutedActions <- executedActions.get
    } yield {
      // Only the second action should have been executed
      assertEquals(finalExecutedActions, List(Set(directAction2)))
      assertEquals(
        finalExecutedActions.length,
        1,
        "First action should have been cancelled"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor passes DirectExecutor errors to ErrorManager") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val delayedAction = Action.Delayed("error-test", directAction, 50.millis)
    val testError = ErrorManager.Error.ExecutionError(
      new RuntimeException("Test error"),
      directAction
    )

    val program = for {
      managedErrors <- Ref.of[IO, List[ErrorManager.Error]](List.empty)

      directExecutor = DirectExecutorStub(
        executeStub = _ => IO.pure(List(testError))
      )
      errorManager = ErrorManagerStub(
        onManageError = error => managedErrors.update(_ :+ error)
      )

      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      // Execute the delayed action
      _ <- scheduledExecutor.execute(Set(delayedAction))

      // Advance time to complete the delay
      _ <- IO.sleep(50.millis)

      // Give the fiber a chance to complete its error handling
      _ <- IO.sleep(1.millis)

      errors <- managedErrors.get
    } yield {
      assertEquals(errors, List(testError))
    }

    TestControl.executeEmbed(program)
  }

  test(
    "ScheduledExecutor handles multiple delayed actions with different IDs"
  ) {
    val directAction1 = Action.SetOpenHabItemValue("item1", "value1")
    val directAction2 = Action.SendMqttStringMessage("topic", "message")
    val delayedAction1 = Action.Delayed("id1", directAction1, 50.millis)
    val delayedAction2 = Action.Delayed("id2", directAction2, 100.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      // Execute both delayed actions
      _ <- scheduledExecutor.execute(Set(delayedAction1, delayedAction2))

      // Advance time to complete both delays
      _ <- IO.sleep(100.millis)

      // Give the fibers a chance to complete their execution
      _ <- IO.sleep(1.millis)

      finalExecutedActions <- executedActions.get
    } yield {
      // Both actions should have been executed
      assertEquals(finalExecutedActions.length, 2)
      assert(finalExecutedActions.contains(Set(directAction1)))
      assert(finalExecutedActions.contains(Set(directAction2)))
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor returns empty error list on successful execution") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val delayedAction = Action.Delayed("success-test", directAction, 50.millis)

    val directExecutor = DirectExecutorStub()
    val errorManager = ErrorManagerStub()

    for {
      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      // Execute the delayed action
      errors <- scheduledExecutor.execute(Set(delayedAction))
    } yield {
      assertEquals(errors, List.empty)
    }
  }

  test("ScheduledExecutor handles empty action set") {
    val directExecutor = DirectExecutorStub()
    val errorManager = ErrorManagerStub()

    for {
      scheduledExecutor <- ScheduledExecutor(directExecutor, errorManager).use(
        IO.pure
      )

      errors <- scheduledExecutor.execute(Set.empty)
    } yield {
      assertEquals(errors, List.empty)
    }
  }

  test("ScheduledExecutor fiber cleanup after execution") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val delayedAction = Action.Delayed("cleanup-test", directAction, 50.millis)

    val program = for {
      executionCount <- Ref.of[IO, Int](0)

      directExecutor = DirectExecutorStub(
        onExecute = _ => executionCount.update(_ + 1)
      )
      errorManager = ErrorManagerStub()

      // Create scheduler and get the fibersRef to inspect
      scheduledExecutorResource = ScheduledExecutor(
        directExecutor,
        errorManager
      )
      result <- scheduledExecutorResource.use { scheduledExecutor =>
        // Access the internal implementation to check fiber cleanup
        val impl = scheduledExecutor.asInstanceOf[ScheduledExecutor.Impl]

        for {
          // Execute delayed action
          _ <- scheduledExecutor.execute(Set(delayedAction))

          // Check that fiber is present while action is pending
          fibersDuringExecution <- impl.fibersRef.get

          // Advance time to complete the delay
          _ <- IO.sleep(50.millis)

          // Give the fiber a chance to complete cleanup
          _ <- IO.sleep(1.millis)

          // Check that fiber is cleaned up after execution
          fibersAfterExecution <- impl.fibersRef.get

          executions <- executionCount.get
        } yield (fibersDuringExecution, fibersAfterExecution, executions)
      }

      (fibersDuring, fibersAfter, executions) = result
    } yield {
      assertEquals(
        fibersDuring.size,
        1,
        "Fiber should be present during execution"
      )
      assertEquals(
        fibersAfter.size,
        0,
        "Fiber should be cleaned up after execution"
      )
      assertEquals(
        executions,
        1,
        "Action should have been executed exactly once"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor executes periodic action repeatedly") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val periodicAction = Action.Periodic("periodic1", directAction, 50.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Execute the periodic action
            _ <- scheduledExecutor.execute(Set(periodicAction))

            // Advance time to see multiple executions
            _ <- IO.sleep(50.millis) // First execution
            _ <- IO.sleep(1.millis) // Buffer for completion
            actionsAfterFirst <- executedActions.get

            _ <- IO.sleep(50.millis) // Second execution
            _ <- IO.sleep(1.millis) // Buffer for completion
            actionsAfterSecond <- executedActions.get

            _ <- IO.sleep(50.millis) // Third execution
            _ <- IO.sleep(1.millis) // Buffer for completion
            actionsAfterThird <- executedActions.get
          } yield (actionsAfterFirst, actionsAfterSecond, actionsAfterThird)
      }

      (actionsAfterFirst, actionsAfterSecond, actionsAfterThird) = result
    } yield {
      assertEquals(
        actionsAfterFirst.length,
        1,
        "First execution should have occurred"
      )
      assertEquals(
        actionsAfterSecond.length,
        2,
        "Second execution should have occurred"
      )
      assertEquals(
        actionsAfterThird.length,
        3,
        "Third execution should have occurred"
      )

      // All executions should be the same action
      actionsAfterThird.foreach { actions =>
        assertEquals(actions, Set(directAction))
      }
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor cancels previous periodic action with same ID") {
    val directAction1 = Action.SetOpenHabItemValue("item1", "value1")
    val directAction2 = Action.SetOpenHabItemValue("item2", "value2")
    val periodicAction1 = Action.Periodic("same-id", directAction1, 100.millis)
    val periodicAction2 = Action.Periodic("same-id", directAction2, 50.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Start first periodic action
            _ <- scheduledExecutor.execute(Set(periodicAction1))

            // Wait some time, then start second periodic action with same ID
            _ <- IO.sleep(25.millis)
            _ <- scheduledExecutor.execute(Set(periodicAction2))

            // Wait for second action's first execution
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            actionsAfterFirstExecution <- executedActions.get

            // Wait for second action's second execution
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            actionsAfterSecondExecution <- executedActions.get

            // Wait to ensure first action would have executed if not cancelled
            _ <- IO.sleep(100.millis)
            finalActions <- executedActions.get
          } yield (
            actionsAfterFirstExecution,
            actionsAfterSecondExecution,
            finalActions
          )
      }

      (actionsAfterFirstExecution, actionsAfterSecondExecution, finalActions) =
        result
    } yield {
      // Only action2 should have been executed
      assertEquals(actionsAfterFirstExecution.length, 1)
      assertEquals(actionsAfterSecondExecution.length, 2)

      finalActions.foreach { actions =>
        assertEquals(
          actions,
          Set(directAction2),
          "Only second action should execute"
        )
      }
    }

    TestControl.executeEmbed(program)
  }

  test(
    "ScheduledExecutor passes periodic DirectExecutor errors to ErrorManager"
  ) {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val periodicAction =
      Action.Periodic("error-periodic", directAction, 50.millis)
    val testError = ErrorManager.Error.ExecutionError(
      new RuntimeException("Periodic test error"),
      directAction
    )

    val program = for {
      managedErrors <- Ref.of[IO, List[ErrorManager.Error]](List.empty)

      directExecutor = DirectExecutorStub(
        executeStub = _ => IO.pure(List(testError))
      )
      errorManager = ErrorManagerStub(
        onManageError = error => managedErrors.update(_ :+ error)
      )

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Execute the periodic action
            _ <- scheduledExecutor.execute(Set(periodicAction))

            // Wait for first execution and error
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            errorsAfterFirst <- managedErrors.get

            // Wait for second execution and error
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            errorsAfterSecond <- managedErrors.get
          } yield (errorsAfterFirst, errorsAfterSecond)
      }

      (errorsAfterFirst, errorsAfterSecond) = result
    } yield {
      assertEquals(errorsAfterFirst.length, 1, "First error should be managed")
      assertEquals(
        errorsAfterSecond.length,
        2,
        "Second error should be managed"
      )
      errorsAfterSecond.foreach { error =>
        assertEquals(error, testError)
      }
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor handles mixed delayed and periodic actions") {
    val delayedAction = Action.SetOpenHabItemValue("item1", "delayed")
    val periodicAction = Action.SendMqttStringMessage("topic", "periodic")
    val delayed = Action.Delayed("delayed-id", delayedAction, 75.millis)
    val periodic = Action.Periodic("periodic-id", periodicAction, 50.millis)

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Execute both actions
            _ <- scheduledExecutor.execute(Set(delayed, periodic))

            // First periodic execution at 50ms
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            actionsAt50ms <- executedActions.get

            // Delayed execution at 75ms
            _ <- IO.sleep(25.millis)
            _ <- IO.sleep(1.millis)
            actionsAt75ms <- executedActions.get

            // Second periodic execution at 100ms
            _ <- IO.sleep(25.millis)
            _ <- IO.sleep(1.millis)
            actionsAt100ms <- executedActions.get
          } yield (actionsAt50ms, actionsAt75ms, actionsAt100ms)
      }

      (actionsAt50ms, actionsAt75ms, actionsAt100ms) = result
    } yield {
      assertEquals(actionsAt50ms.length, 1, "First periodic execution")
      assertEquals(
        actionsAt75ms.length,
        2,
        "Delayed execution + first periodic"
      )
      assertEquals(actionsAt100ms.length, 3, "Second periodic execution")

      // Check that we have both types of actions
      val allActions = actionsAt100ms.flatten.toSet
      assert(
        allActions.contains(delayedAction),
        "Delayed action should be executed"
      )
      assert(
        allActions.contains(periodicAction),
        "Periodic action should be executed"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor cancels delayed action with Cancel action") {
    val directAction = Action.SetOpenHabItemValue("item1", "value1")
    val delayedAction = Action.Delayed("cancel-test", directAction, 100.millis)
    val cancelAction = Action.Cancel("cancel-test")

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Start a delayed action
            _ <- scheduledExecutor.execute(Set(delayedAction))

            // Wait partway through the delay
            _ <- IO.sleep(50.millis)
            actionsAfter50ms <- executedActions.get

            // Cancel the action
            _ <- scheduledExecutor.execute(Set(cancelAction))

            // Wait for the original delay to complete
            _ <- IO.sleep(60.millis)
            _ <- IO.sleep(1.millis)
            finalActions <- executedActions.get
          } yield (actionsAfter50ms, finalActions)
      }

      (actionsAfter50ms, finalActions) = result
    } yield {
      assertEquals(
        actionsAfter50ms,
        List.empty,
        "Action should not execute before delay"
      )
      assertEquals(
        finalActions,
        List.empty,
        "Action should not execute after cancellation"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor cancels periodic action with Cancel action") {
    val directAction = Action.SendMqttStringMessage("topic", "message")
    val periodicAction =
      Action.Periodic("cancel-periodic", directAction, 50.millis)
    val cancelAction = Action.Cancel("cancel-periodic")

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Start a periodic action
            _ <- scheduledExecutor.execute(Set(periodicAction))

            // Wait for first execution
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            actionsAfterFirst <- executedActions.get

            // Cancel the periodic action
            _ <- scheduledExecutor.execute(Set(cancelAction))

            // Wait for what would be the second execution
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            finalActions <- executedActions.get
          } yield (actionsAfterFirst, finalActions)
      }

      (actionsAfterFirst, finalActions) = result
    } yield {
      assertEquals(actionsAfterFirst.length, 1, "First execution should occur")
      assertEquals(
        finalActions.length,
        1,
        "No second execution should occur after cancellation"
      )
      assertEquals(finalActions.head, Set(directAction))
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor handles Cancel for non-existent ID gracefully") {
    val cancelAction = Action.Cancel("non-existent-id")

    val directExecutor = DirectExecutorStub()
    val errorManager = ErrorManagerStub()

    val program = for {
      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Try to cancel a non-existent action
            errors <- scheduledExecutor.execute(Set(cancelAction))
          } yield errors
      }
    } yield {
      assertEquals(
        result,
        List.empty,
        "No errors should occur when cancelling non-existent ID"
      )
    }

    TestControl.executeEmbed(program)
  }

  test("ScheduledExecutor handles multiple Cancel actions") {
    val directAction1 = Action.SetOpenHabItemValue("item1", "value1")
    val directAction2 = Action.SendMqttStringMessage("topic", "message")
    val delayedAction1 = Action.Delayed("id1", directAction1, 100.millis)
    val periodicAction2 = Action.Periodic("id2", directAction2, 50.millis)
    val cancelAction1 = Action.Cancel("id1")
    val cancelAction2 = Action.Cancel("id2")

    val program = for {
      executedActions <- Ref.of[IO, List[Set[Action.Direct]]](List.empty)

      directExecutor = DirectExecutorStub(
        onExecute = actions => executedActions.update(_ :+ actions)
      )
      errorManager = ErrorManagerStub()

      result <- ScheduledExecutor(directExecutor, errorManager).use {
        scheduledExecutor =>
          for {
            // Start both actions
            _ <- scheduledExecutor.execute(Set(delayedAction1, periodicAction2))

            // Wait for periodic's first execution
            _ <- IO.sleep(50.millis)
            _ <- IO.sleep(1.millis)
            actionsAfterFirstPeriodic <- executedActions.get

            // Cancel both actions
            _ <- scheduledExecutor.execute(Set(cancelAction1, cancelAction2))

            // Wait for what would be delayed execution and second periodic
            _ <- IO.sleep(60.millis)
            _ <- IO.sleep(1.millis)
            finalActions <- executedActions.get
          } yield (actionsAfterFirstPeriodic, finalActions)
      }

      (actionsAfterFirstPeriodic, finalActions) = result
    } yield {
      assertEquals(
        actionsAfterFirstPeriodic.length,
        1,
        "Only first periodic execution should occur"
      )
      assertEquals(
        finalActions.length,
        1,
        "No further executions should occur after cancellation"
      )
      assertEquals(finalActions.head, Set(directAction2))
    }

    TestControl.executeEmbed(program)
  }
}
