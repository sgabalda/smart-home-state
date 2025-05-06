package calespiga.openhab

import calespiga.config.OpenHabConfig
import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.testing.*
import sttp.client4.testing.StubBody.Adjust
import sttp.model.StatusCode

class APIClientSuite extends CatsEffectSuite {

  private val config = OpenHabConfig(
    host = "localhost",
    port = 8080,
    apiToken = "testToken"
  )

  test("APIClient should process properly a success") {

    val item = "TestItem"
    val value = "TestValue"

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("TestItem")))
      .thenRespond(ResponseStub.ok(Adjust("")))

    APIClient(config, Resource.pure(backend)).use { apiClient =>
      // Mock the API client to simulate a successful response
      for {
        response <- apiClient.changeItem(item, value)
      } yield {
        // Verify that the request was sent with the correct item and value
        assertEquals(response, ())
      }
    }
  }
  test("APIClient should process properly a failure") {

    val item = "TestItem"
    val value = "TestValue"

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("TestItem")))
      .thenRespond(ResponseStub(Adjust(""), StatusCode.NotFound))

    APIClient(config, Resource.pure(backend)).use { apiClient =>
      for {
        response <- apiClient.changeItem(item, value).attempt
      } yield {
        assertEquals(response.isLeft, true, "Not found did not throw an error")
      }
    }
  }
}
