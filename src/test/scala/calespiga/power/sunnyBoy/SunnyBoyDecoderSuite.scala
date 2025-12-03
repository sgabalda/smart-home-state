package calespiga.power.sunnyBoy

import munit.FunSuite
import calespiga.config.SunnyBoyConfig

class SunnyBoyDecoderSuite extends FunSuite {

  // Dummy config for testing
  val config = SunnyBoyConfig(
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

  val decoder = SunnyBoyDecoder(config)

  // ============================================================
  // getToken tests
  // ============================================================

  test("getToken: successful response returns token") {
    val successJson = """{"result": {"sid": "ABC123XYZ"}}"""
    val result = decoder.getToken(successJson)
    assertEquals(result, Right("ABC123XYZ"))
  }

  test("getToken: error response returns failure") {
    val errorJson = """{"err": "invalid credentials"}"""
    val result = decoder.getToken(errorJson)
    assert(result.isLeft, "Error response should return Left")
  }

  test("getToken: malformed JSON returns failure") {
    val malformedJson = """{"unexpected": "format"}"""
    val result = decoder.getToken(malformedJson)
    assert(result.isLeft)
  }

  test("getToken: invalid JSON syntax returns failure") {
    val invalidJson = """{this is not valid json}"""
    val result = decoder.getToken(invalidJson)
    assert(result.isLeft)
  }

  // ============================================================
  // getData tests
  // ============================================================

  test("getData: successful response with single line returns data") {
    val successJson = s"""{
      "result": {
        "${config.serialId}": {
          "${config.totalPowerCode}": {
            "1": [
              {"val": 5000.0}
            ]
          },
          "${config.frequencyCode}": {
            "1": [
              {"val": 50.5}
            ]
          },
          "${config.linesCode}": {
            "1": [
              {"val": 1500.0}
            ]
          }
        }
      }
    }"""
    val result = decoder.getData(successJson)
    result match {
      case Right(dataResponse) =>
        assertEquals(dataResponse.generatedPower, 5000.0f)
        assertEquals(dataResponse.frequency, 50.5f)
        assertEquals(dataResponse.linesPower, List(1500.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test("getData: successful response with multiple lines returns data") {
    val successJson = s"""{
      "result": {
        "${config.serialId}": {
          "${config.totalPowerCode}": {
            "1": [
              {"val": 8000.0}
            ]
          },
          "${config.frequencyCode}": {
            "1": [
              {"val": 51.8}
            ]
          },
          "${config.linesCode}": {
            "1": [
              {"val": 2000.0},
              {"val": 3000.0},
              {"val": 2500.0}
            ]
          }
        }
      }
    }"""
    val result = decoder.getData(successJson)
    result match {
      case Right(dataResponse) =>
        assertEquals(dataResponse.generatedPower, 8000.0f)
        assertEquals(dataResponse.frequency, 51.8f)
        assertEquals(dataResponse.linesPower, List(2000.0f, 3000.0f, 2500.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test("getData: error response returns failure") {
    val errorJson = """{"err": "session timeout"}"""
    val result = decoder.getData(errorJson)
    assert(result.isLeft, "Error response should return Left")
  }

  test("getData: malformed response returns failure") {
    val malformedJson = """{"result": {"wrongStructure": true}}"""
    val result = decoder.getData(malformedJson)
    assert(result.isLeft)
  }

  test("getData: missing field in response returns failure") {
    val missingFieldJson = s"""{
      "result": {
        "${config.serialId}": {
          "${config.totalPowerCode}": {
            "1": [
              {"val": 5000.0}
            ]
          }
        }
      }
    }"""
    val result = decoder.getData(missingFieldJson)
    assert(result.isLeft)
  }

  // ============================================================
  // toPowerProduction tests
  // ============================================================

  test("toPowerProduction: frequency >= 52Hz means max power available") {
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 8000.0f,
      frequency = 52.0f,
      linesPower = List(2000.0f, 3000.0f, 3000.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        assertEquals(powerData.powerAvailable, config.maxPowerAvailable)
        assertEquals(powerData.powerProduced, 8000.0f)
        assertEquals(
          powerData.powerDiscarded,
          config.maxPowerAvailable - 8000.0f
        )
        assertEquals(powerData.linesPower, List(2000.0f, 3000.0f, 3000.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test("toPowerProduction: frequency > 52Hz means max power available") {
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 9500.0f,
      frequency = 52.5f,
      linesPower = List(3000.0f, 3000.0f, 3500.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        assertEquals(powerData.powerAvailable, config.maxPowerAvailable)
        assertEquals(powerData.powerProduced, 9500.0f)
        assertEquals(
          powerData.powerDiscarded,
          config.maxPowerAvailable - 9500.0f
        )
        assertEquals(powerData.linesPower, List(3000.0f, 3000.0f, 3500.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test("toPowerProduction: frequency <= 51Hz means all power is produced") {
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 6000.0f,
      frequency = 51.0f,
      linesPower = List(2000.0f, 2000.0f, 2000.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        assertEquals(powerData.powerAvailable, 6000.0f)
        assertEquals(powerData.powerProduced, 6000.0f)
        assertEquals(powerData.powerDiscarded, 0.0f)
        assertEquals(powerData.linesPower, List(2000.0f, 2000.0f, 2000.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test("toPowerProduction: frequency < 51Hz means all power is produced") {
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = 4500.0f,
      frequency = 50.5f,
      linesPower = List(1500.0f, 1500.0f, 1500.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        assertEquals(powerData.powerAvailable, 4500.0f)
        assertEquals(powerData.powerProduced, 4500.0f)
        assertEquals(powerData.powerDiscarded, 0.0f)
        assertEquals(powerData.linesPower, List(1500.0f, 1500.0f, 1500.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test(
    "toPowerProduction: frequency between 51Hz and 52Hz estimates available power"
  ) {
    val generatedPower = 7000.0f
    val frequency = 51.5f
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = generatedPower,
      frequency = frequency,
      linesPower = List(2300.0f, 2300.0f, 2400.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        // availablePowerEstimation = generatedPower / (52.0f - frequency)
        val expectedEstimation = generatedPower / (52.0f - frequency)
        val expectedAvailable =
          Math.min(expectedEstimation, config.maxPowerAvailable)
        val expectedDiscarded = expectedAvailable - generatedPower

        assertEquals(powerData.powerAvailable, expectedAvailable)
        assertEquals(powerData.powerProduced, generatedPower)
        assertEquals(powerData.powerDiscarded, expectedDiscarded)
        assertEquals(powerData.linesPower, List(2300.0f, 2300.0f, 2400.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }

  test(
    "toPowerProduction: frequency between 51Hz and 52Hz caps at max available"
  ) {
    // Test case where estimation would exceed max, so it gets capped
    val generatedPower = 9900.0f
    val frequency = 51.99f // Very close to 52, would give huge estimation
    val dataResponse = SunnyBoyDecoder.DataResponse(
      generatedPower = generatedPower,
      frequency = frequency,
      linesPower = List(3300.0f, 3300.0f, 3300.0f)
    )
    val result = decoder.toPowerProduction(dataResponse)
    result match {
      case Right(powerData) =>
        // Estimation would be very high, but should be capped at maxPowerAvailable
        assertEquals(powerData.powerAvailable, config.maxPowerAvailable)
        assertEquals(powerData.powerProduced, generatedPower)
        assertEquals(
          powerData.powerDiscarded,
          config.maxPowerAvailable - generatedPower
        )
        assertEquals(powerData.linesPower, List(3300.0f, 3300.0f, 3300.0f))
      case Left(error) =>
        fail(s"Expected Right but got Left: ${error.getMessage}")
    }
  }
}
