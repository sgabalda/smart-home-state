package calespiga.shared

import io.circe.Printer
import io.circe.syntax._
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import sttp.apispec.openapi.circe._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object ApiTypesGenerator {
  def main(args: Array[String]): Unit = {
    require(
      args.length == 1,
      "Expected the output path for the OpenAPI document"
    )
    val output = Paths.get(args(0))
    val document = OpenAPIDocsInterpreter()
      .toOpenAPI(
        List(ApiContracts.systemStatusEndpoint),
        "Smart Home State API",
        "1.0"
      )
    writeOpenApiDocument(output, Printer.noSpaces.print(document.asJson))
  }

  private def writeOpenApiDocument(output: Path, contents: String): Unit = {
    Option(output.getParent).foreach(parent => Files.createDirectories(parent))
    val _ = Files.writeString(output, contents)
  }
}
