import scala.sys.process.*

ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / semanticdbEnabled := true

lazy val generateTsTypes =
  taskKey[Unit]("Generate TypeScript types from the shared Tapir API")

lazy val shared = (project in file("shared"))
  .settings(
    name := "smart-home-state-shared",
    Compile / run / fork := false,
    libraryDependencies ++= Dependencies.sharedDependencies,
    scalacOptions := scalacOptions.value
      .filterNot(_ == "-Xfatal-warnings") :+ "-Werror"
  )

lazy val backend = (project in file("backend"))
  .dependsOn(shared)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "smart-home-state",
    libraryDependencies ++= Dependencies.backendDependencies ++ Dependencies.backendTesting,
    Compile / run / fork := true,
    Universal / stage / target := baseDirectory.value / "target" / "universal",
    Universal / target := baseDirectory.value / "target" / "universal",
    scalacOptions := scalacOptions.value
      .filterNot(_ == "-Xfatal-warnings") :+ "-Werror"
  )

lazy val frontend = (project in file("frontend"))
  .dependsOn(shared)
  .settings(
    name := "smart-home-state-frontend",
    Compile / sources := Nil,
    Test / sources := Nil,
    publish / skip := true
  )

lazy val root = (project in file("."))
  .aggregate(shared, backend, frontend)
  .settings(
    name := "smart-home-state-root",
    publish / skip := true,
    generateTsTypes := Def.uncached {
      val repoRoot = baseDirectory.value
      val openApiFile = repoRoot / "frontend" / "target" / "openapi.json"
      val generatedTypes =
        repoRoot / "frontend" / "src" / "generated" / "api-types.ts"
      val generator =
        repoRoot / "frontend" / "node_modules" / ".bin" / "openapi-typescript"

      IO.createDirectory(openApiFile.getParentFile)
      IO.createDirectory(generatedTypes.getParentFile)
      val classpath =
        (shared / Compile / fullClasspath).value.map(entry =>
          fileConverter.value.toPath(entry.data)
        )
      val scalaRunner = (shared / Compile / runner).value
      scalaRunner
        .run(
          "calespiga.shared.ApiTypesGenerator",
          classpath,
          Seq(openApiFile.toPath.toString),
          streams.value.log
        )
        .get

      if (!generator.exists) {
        sys.error(
          "openapi-typescript is missing; run `npm --prefix frontend install` first"
        )
      }

      val exitCode = Process(
        Seq(
          generator.getAbsolutePath,
          openApiFile.getAbsolutePath,
          "--output",
          generatedTypes.getAbsolutePath
        ),
        repoRoot
      ).!
      if (exitCode != 0)
        sys.error(s"openapi-typescript failed with exit code $exitCode")

      IO.append(
        generatedTypes,
        "\nexport type SystemStatus = components[\"schemas\"][\"SystemStatus\"];\n"
      )
    }
  )
