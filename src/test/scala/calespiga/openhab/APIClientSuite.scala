package calespiga.openhab

import calespiga.config.OpenHabConfig
import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.testing.*
import sttp.client4.testing.StubBody.Adjust
import sttp.model.StatusCode
import scala.concurrent.duration.*
import calespiga.HealthComponentManagerStub
import cats.effect.Ref

class APIClientSuite extends CatsEffectSuite {

  private val config = OpenHabConfig(
    host = "localhost",
    port = 8080,
    apiToken = "testToken",
    retryDelay = 5.seconds
  )

  test("APIClient should process properly a success") {

    val item = "TestItem"
    val value = "TestValue"

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("TestItem")))
      .thenRespond(ResponseStub.ok(Adjust("")))

    Ref[IO].of[Option[Boolean]](None).flatMap { ref =>
      APIClient(
        config,
        healthRestApi = HealthComponentManagerStub(
          onHealthy = ref.set(Some(true)),
          onUnhealthy = ref.set(Some(false))
        ),
        healthWebSocket = HealthComponentManagerStub(),
        Resource.pure(backend)
      ).use { apiClient =>
        // Mock the API client to simulate a successful response
        for {
          response <- apiClient.changeItem(item, value)
          health <- ref.get
        } yield {
          // Verify that the request was sent with the correct item and value
          assertEquals(response, ())
          assertEquals(health, Some(true))
        }
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

    Ref[IO].of[Option[Boolean]](None).flatMap { ref =>
      APIClient(
        config,
        healthRestApi = HealthComponentManagerStub(
          onHealthy = ref.set(Some(true)),
          onUnhealthy = ref.set(Some(false))
        ),
        healthWebSocket = HealthComponentManagerStub(),
        Resource.pure(backend)
      ).use { apiClient =>
        for {
          response <- apiClient.changeItem(item, value).attempt
          health <- ref.get
        } yield {
          assertEquals(
            response.isLeft,
            true,
            "Not found did not throw an error"
          )
          assertEquals(health, Some(false))
        }
      }
    }
  }
}
