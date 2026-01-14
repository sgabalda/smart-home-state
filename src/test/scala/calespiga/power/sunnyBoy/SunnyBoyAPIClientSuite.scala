package calespiga.power.sunnyBoy

import calespiga.config.SunnyBoyConfig
import calespiga.power.PowerProductionData
import cats.effect.{IO, Resource, Ref}
import munit.CatsEffectSuite
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.testing.*
import sttp.client4.testing.StubBody.Adjust
import sttp.model.StatusCode

class SunnyBoyAPIClientSuite extends CatsEffectSuite {

  private val config = SunnyBoyConfig(
    username = "test-user",
    password = "test-pass",
    loginUrl = "http://test.local/login",
    dataUrl = "http://test.local/data",
    totalPowerCode = "6100_40263F00",
    frequencyCode = "6100_00465700",
    linesCode = "6380_40251E00",
    serialId = "123456789",
    maxPowerAvailable = 10000.0f
  )

  // Stub decoder that can be configured per test
  case class StubDecoder(
      tokenResult: Either[Throwable, String],
      dataResult: Either[Throwable, SunnyBoyDecoder.DataResponse],
      powerProductionResult: Either[Throwable, PowerProductionData]
  ) extends SunnyBoyDecoder {
    override def getToken(responseBody: String): Either[Throwable, String] =
      tokenResult

    override def getData(
        responseBody: String
    ): Either[Throwable, SunnyBoyDecoder.DataResponse] =
      dataResult

    override def toPowerProduction(
        dataResponse: SunnyBoyDecoder.DataResponse
    ): Either[Throwable, PowerProductionData] =
      powerProductionResult
  }

  val successTokenJson = """{"result": {"sid": "TOKEN123"}}"""
  val successDataJson = """{"result": {"data": "some data"}}"""

  test("getCurrentPowerData: first call obtains token and retrieves data") {
    val expectedData = PowerProductionData(
      powerAvailable = 8000.0f,
      powerProduced = 7500.0f,
      powerDiscarded = 500.0f,
      linesPower = List(2500.0f, 2500.0f, 2500.0f)
    )

    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 7500.0f,
      frequency = 51.5f,
      linesPower = List(2500.0f, 2500.0f, 2500.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult = Right(expectedData)
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData
        } yield {
          assertEquals(result, expectedData)
        }
    }
  }

  test("getCurrentPowerData: uses cached token on subsequent calls") {
    val expectedData = PowerProductionData(
      powerAvailable = 9000.0f,
      powerProduced = 8500.0f,
      powerDiscarded = 500.0f,
      linesPower = List(2800.0f, 2800.0f, 2900.0f)
    )

    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 8500.0f,
      frequency = 51.8f,
      linesPower = List(2800.0f, 2800.0f, 2900.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult = Right(expectedData)
    )

    val callCounter = Ref.unsafe[IO, Int](0)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespondF { _ =>
        callCounter.update(_ + 1).as(ResponseStub.ok(Adjust(successTokenJson)))
      }
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          _ <- apiClient.getCurrentPowerData
          _ <- apiClient.getCurrentPowerData
          loginCalls <- callCounter.get
        } yield {
          assertEquals(
            loginCalls,
            1,
            "Login should only be called once, not on subsequent calls"
          )
        }
    }
  }

  test(
    "getCurrentPowerData: retries with new token when data request fails"
  ) {
    val expectedData = PowerProductionData(
      powerAvailable = 7000.0f,
      powerProduced = 6500.0f,
      powerDiscarded = 500.0f,
      linesPower = List(2100.0f, 2200.0f, 2200.0f)
    )

    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 6500.0f,
      frequency = 51.3f,
      linesPower = List(2100.0f, 2200.0f, 2200.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult = Right(expectedData)
    )

    val dataCallCounter = Ref.unsafe[IO, Int](0)
    val loginCallCounter = Ref.unsafe[IO, Int](0)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespondF { _ =>
        loginCallCounter
          .update(_ + 1)
          .as(ResponseStub.ok(Adjust(successTokenJson)))
      }
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespondF { _ =>
        dataCallCounter.getAndUpdate(_ + 1).flatMap { count =>
          if (count == 0)
            IO.pure(
              ResponseStub(Adjust("error"), StatusCode.Unauthorized)
            ) // First call fails
          else
            IO.pure(
              ResponseStub.ok(Adjust(successDataJson))
            ) // Second call succeeds
        }
      }

    SunnyBoyAPIClient(
      config,
      decoder,
      Resource.pure(backend),
      initialToken = Some("TOKEN123")
    ).use { apiClient =>
      for {
        result <- apiClient.getCurrentPowerData
        dataCalls <- dataCallCounter.get
        loginCalls <- loginCallCounter.get
      } yield {
        assertEquals(result, expectedData)
        assertEquals(dataCalls, 2, "Data endpoint should be called twice")
        assertEquals(
          loginCalls,
          1,
          "Login should be called once (initial token used, then retry after error)"
        )
      }
    }
  }

  test("updateToken: fails when API returns error status") {
    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(
        SunnyBoyDecoder.DataResponse(1000.0f, 50.0f, List(1000.0f))
      ),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub(Adjust("error"), StatusCode.Unauthorized))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData.attempt
        } yield {
          assert(result.isLeft, "Should fail when login returns error status")
        }
    }
  }

  test("updateToken: when failed, retries after session restart") {
    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(
        SunnyBoyDecoder.DataResponse(1000.0f, 50.0f, List(1000.0f))
      ),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val loginCallCounter = Ref.unsafe[IO, Int](0)
    val sessionRestartCounter = Ref.unsafe[IO, Int](0)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespondF { _ =>
        loginCallCounter.getAndUpdate(_ + 1).flatMap { count =>
          if (count == 0)
            IO.pure(
              ResponseStub(Adjust("error"), StatusCode.Unauthorized)
            ) // First call fails
          else
            IO.pure(
              ResponseStub.ok(Adjust(successTokenJson))
            ) // Second call succeeds
        }
      }
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(
      config,
      decoder,
      Resource.pure(backend),
      sessionRestartEffect = sessionRestartCounter.update(_ + 1).void
    ).use { apiClient =>
      for {
        result <- apiClient.getCurrentPowerData.attempt
        loginCalls <- loginCallCounter.get
        sessionRestarts <- sessionRestartCounter.get
      } yield {
        assert(
          result.isRight,
          "Should succeed when login returns error status but succeeds after"
        )
        assertEquals(
          loginCalls,
          2,
          "Login should be called twice due to retry after failure"
        )
        assertEquals(
          sessionRestarts,
          1,
          "Session restart effect should be invoked once after first failure"
        )
      }
    }
  }

  test("updateToken: when failed after session restart, fail with error") {
    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(
        SunnyBoyDecoder.DataResponse(1000.0f, 50.0f, List(1000.0f))
      ),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val loginCallCounter = Ref.unsafe[IO, Int](0)
    val sessionRestartCounter = Ref.unsafe[IO, Int](0)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespondF { _ =>
        loginCallCounter.getAndUpdate(_ + 1).flatMap { _ =>
          IO.pure(
            ResponseStub(Adjust("error"), StatusCode.Unauthorized)
          ) // all calls fail
        }
      }

    SunnyBoyAPIClient(
      config,
      decoder,
      Resource.pure(backend),
      sessionRestartEffect = sessionRestartCounter.update(_ + 1).void
    ).use { apiClient =>
      for {
        result <- apiClient.getCurrentPowerData.attempt
        loginCalls <- loginCallCounter.get
        sessionRestarts <- sessionRestartCounter.get
      } yield {
        assert(
          result.isLeft,
          "Should fail when login returns error status even after session restart"
        )
        assertEquals(
          loginCalls,
          2,
          "Login should be called twice due to retry after failure"
        )
        assertEquals(
          sessionRestarts,
          1,
          "Session restart effect should be invoked once after first failure"
        )
      }
    }
  }

  test("updateToken: fails when decoder returns error") {
    val decoder = StubDecoder(
      tokenResult = Left(new Exception("Invalid token format")),
      dataResult = Right(
        SunnyBoyDecoder.DataResponse(1000.0f, 50.0f, List(1000.0f))
      ),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData.attempt
        } yield {
          assert(result.isLeft, "Should fail when decoder fails to parse token")
          result match {
            case Left(error) =>
              assert(error.getMessage.contains("Invalid token format"))
            case Right(_) => fail("Expected Left but got Right")
          }
        }
    }
  }

  test("getData: fails when API returns error status") {
    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(
        SunnyBoyDecoder.DataResponse(1000.0f, 50.0f, List(1000.0f))
      ),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(
        ResponseStub(Adjust("error"), StatusCode.InternalServerError)
      )

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData.attempt
        } yield {
          assert(result.isLeft, "Should fail when data request returns error")
        }
    }
  }

  test("getData: fails when decoder fails to parse data") {
    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Left(new Exception("Invalid data format")),
      powerProductionResult =
        Right(PowerProductionData(1000.0f, 1000.0f, 0.0f, List(1000.0f)))
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData.attempt
        } yield {
          assert(result.isLeft, "Should fail when decoder fails to parse data")
          result match {
            case Left(error) =>
              assert(error.getMessage.contains("Invalid data format"))
            case Right(_) => fail("Expected Left but got Right")
          }
        }
    }
  }

  test("getData: fails when toPowerProduction conversion fails") {
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 7500.0f,
      frequency = 51.5f,
      linesPower = List(2500.0f, 2500.0f, 2500.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult =
        Left(new Exception("Power production conversion failed"))
    )

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          result <- apiClient.getCurrentPowerData.attempt
        } yield {
          assert(
            result.isLeft,
            "Should fail when toPowerProduction conversion fails"
          )
          result match {
            case Left(error) =>
              assert(
                error.getMessage.contains("Power production conversion failed")
              )
            case Right(_) => fail("Expected Left but got Right")
          }
        }
    }
  }

  test("getCurrentPowerData: sends correct login request body") {
    val expectedData = PowerProductionData(
      powerAvailable = 5000.0f,
      powerProduced = 4800.0f,
      powerDiscarded = 200.0f,
      linesPower = List(1600.0f, 1600.0f, 1600.0f)
    )

    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 4800.0f,
      frequency = 51.1f,
      linesPower = List(1600.0f, 1600.0f, 1600.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult = Right(expectedData)
    )

    val requestBodyRef = Ref.unsafe[IO, Option[String]](None)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespondF { request =>
        requestBodyRef
          .set(Some(request.body.toString))
          .as(ResponseStub.ok(Adjust(successTokenJson)))
      }
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespond(ResponseStub.ok(Adjust(successDataJson)))

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          _ <- apiClient.getCurrentPowerData
          requestBody <- requestBodyRef.get
        } yield {
          assert(requestBody.isDefined, "Request body should be captured")
          assert(
            requestBody.get.contains("test-user"),
            "Request should contain username"
          )
          assert(
            requestBody.get.contains("test-pass"),
            "Request should contain password"
          )
        }
    }
  }

  test("getCurrentPowerData: sends token as query parameter in data request") {
    val expectedData = PowerProductionData(
      powerAvailable = 6000.0f,
      powerProduced = 5800.0f,
      powerDiscarded = 200.0f,
      linesPower = List(1900.0f, 1900.0f, 2000.0f)
    )

    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 5800.0f,
      frequency = 51.2f,
      linesPower = List(1900.0f, 1900.0f, 2000.0f)
    )

    val decoder = StubDecoder(
      tokenResult = Right("TOKEN123"),
      dataResult = Right(dataResponse),
      powerProductionResult = Right(expectedData)
    )

    val dataUriRef = Ref.unsafe[IO, Option[String]](None)

    val backend = HttpClientCatsBackend
      .stub[IO]
      .whenRequestMatches(_.uri.path.endsWith(List("login")))
      .thenRespond(ResponseStub.ok(Adjust(successTokenJson)))
      .whenRequestMatches(_.uri.path.endsWith(List("data")))
      .thenRespondF { request =>
        dataUriRef
          .set(Some(request.uri.toString))
          .as(ResponseStub.ok(Adjust(successDataJson)))
      }

    SunnyBoyAPIClient(config, decoder, Resource.pure(backend)).use {
      apiClient =>
        for {
          _ <- apiClient.getCurrentPowerData
          dataUri <- dataUriRef.get
        } yield {
          assert(dataUri.isDefined, "Data URI should be captured")
          assert(
            dataUri.get.contains("sid=TOKEN123"),
            "Data request should contain token as query parameter"
          )
        }
    }
  }
}
