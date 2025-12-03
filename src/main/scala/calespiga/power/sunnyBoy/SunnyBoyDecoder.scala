package calespiga.power.sunnyBoy

import io.circe.parser.decode
import io.circe.{Decoder, HCursor}
import cats.implicits.*
import calespiga.config.SunnyBoyConfig
import calespiga.power.sunnyBoy.SunnyBoyDecoder.DataResponse

import calespiga.power.PowerProductionData

trait SunnyBoyDecoder {
  def getToken(responseBody: String): Either[Throwable, String]
  def getData(responseBody: String): Either[Throwable, DataResponse]
  def toPowerProduction(
      dataResponse: DataResponse
  ): Either[Throwable, PowerProductionData]
}

object SunnyBoyDecoder {

  case class DataResponse(
      generatedPower: Float,
      frequency: Float,
      linesPower: List[Float]
  )

  private final case class Impl(config: SunnyBoyConfig)
      extends SunnyBoyDecoder {

    private case class TokenResponse(sid: String)
    private given Decoder[TokenResponse] = (c: HCursor) => {
      c.downField("err")
        .as[String]
        .flatMap { err =>
          Left(io.circe.DecodingFailure(s"SunnyBoy API error: $err", c.history))
        }
        .orElse {
          c.downField("result")
            .downField("sid")
            .as[String]
            .map(TokenResponse.apply)
        }
    }

    private given Decoder[DataResponse] = (c: HCursor) => {
      c.downField("err")
        .as[String]
        .flatMap { err =>
          Left(io.circe.DecodingFailure(s"SunnyBoy API error: $err", c.history))
        }
        .orElse {
          val resultCursor = c.downField("result").downField(config.serialId)

          for {
            totalPower <- resultCursor
              .downField(config.totalPowerCode)
              .downField("1")
              .downN(0)
              .downField("val")
              .as[Float]

            frequency <- resultCursor
              .downField(config.frequencyCode)
              .downField("1")
              .downN(0)
              .downField("val")
              .as[Float]

            linesPowerArray <- resultCursor
              .downField(config.linesCode)
              .downField("1")
              .as[List[io.circe.Json]]

            linesPower <- linesPowerArray
              .traverse(json => json.hcursor.downField("val").as[Float])
          } yield DataResponse(totalPower, frequency, linesPower)
        }
    }

    def getToken(responseBody: String): Either[Throwable, String] = {
      decode[TokenResponse](responseBody) match {
        case Right(TokenResponse(sid)) => Right(sid)
        case Left(error)               => Left(error)

      }
    }

    def getData(responseBody: String): Either[Throwable, DataResponse] = {
      decode[DataResponse](responseBody) match {
        case Right(dataResponse) => Right(dataResponse)
        case Left(error)         => Left(error)

      }
    }

    def toPowerProduction(
        dataResponse: DataResponse
    ): Either[Throwable, PowerProductionData] = {
      dataResponse match {
        case DataResponse(generatedPower, frequency, linesPower)
            if frequency >= 52f =>
          // power available is the maximum
          Right(
            PowerProductionData(
              powerAvailable = config.maxPowerAvailable,
              powerProduced = generatedPower,
              powerDiscarded = config.maxPowerAvailable - generatedPower,
              linesPower = linesPower
            )
          )
        case DataResponse(generatedPower, frequency, linesPower)
            if frequency <= 51f =>
          // all power available is produced
          Right(
            PowerProductionData(
              powerAvailable = generatedPower,
              powerProduced = generatedPower,
              powerDiscarded = 0.0,
              linesPower = linesPower
            )
          )
        case DataResponse(generatedPower, frequency, linesPower) =>
          // some power is produced, some discarded
          val availablePowerEstimation = generatedPower / (52.0f - frequency)
          val availablePower =
            Math.min(availablePowerEstimation, config.maxPowerAvailable)
          val discardedPower = availablePower - generatedPower
          Right(
            PowerProductionData(
              powerAvailable = availablePower,
              powerProduced = generatedPower,
              powerDiscarded = discardedPower,
              linesPower = linesPower
            )
          )
      }
    }
  }

  def apply(config: SunnyBoyConfig): SunnyBoyDecoder = Impl(config)

}
