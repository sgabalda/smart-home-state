package calespiga.shared

import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object ApiContracts {
  val systemStatusEndpoint = endpoint.get
    .in("api" / "status")
    .out(jsonBody[SystemStatus])
}
