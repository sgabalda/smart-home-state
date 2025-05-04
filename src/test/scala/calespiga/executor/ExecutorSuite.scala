package calespiga.executor

import calespiga.ErrorManager
import calespiga.model.Action
import calespiga.openhab.ApiClientStub
import cats.effect.IO
import munit.CatsEffectSuite

class ExecutorSuite extends CatsEffectSuite {

  test("Executor should request to the APIClient on SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"
    
    for{
     called <- IO.ref(false)
        apiClient = ApiClientStub(
            changeItem = (item: String, value: String) => called.set(true)
        )
        executorResource = Executor(apiClient)
        _ <- executorResource.use{executor => executor.execute(Set(Action.SetOpenHabItemValue(item, value)))}
        calledValue <- called.get
    }yield{
      assertEquals(calledValue, true, "APIClient was not called")
    }
  }

  test("Executor should return an error on failure of SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    val error = new Exception("API error")
    val action = Action.SetOpenHabItemValue(item, value)

    Executor(ApiClientStub(
      changeItem = (item: String, value: String) => IO.raiseError(error)
    )).use{      executor =>
        executor.execute(Set(action)).map {
          case List(ErrorManager.Error.ExecutionError(throwable, act)) =>
            assertEquals(throwable, error, "The throwable was not propagated")
            assertEquals(act, action, "The action was not propagated")
          case _ => fail("The error was not propagated")
        }
    }
  }

  test("Executor should return no error on success of SetOpenHabItemValue") {

    val item = "TestItem"
    val value = "TestValue"

    val action = Action.SetOpenHabItemValue(item, value)

    Executor(ApiClientStub(
      changeItem = (item: String, value: String) => IO.unit
    )).use { executor =>
      executor.execute(Set(action)).map {
        case some :: _ => fail("The error was not propagated")
        case Nil => // No error, as expected
      }
    }
  }
}
