ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.9.0"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "smart-home-state",
    libraryDependencies ++= Dependencies.dependencies ++ Dependencies.testing,
    Global / semanticdbEnabled := true,
    Compile / run / fork := true,
    Universal / stage / target := baseDirectory.value / "target" / "universal" / "stage",
    Universal / target := baseDirectory.value / "target" / "universal" / "stage",
    scalacOptions := scalacOptions.value
      .filterNot(_ == "-Xfatal-warnings") :+ "-Werror"
  )
