ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "smart-home-state",
    libraryDependencies ++= Dependencies.dependencies ++ Dependencies.testing,
    Global / semanticdbEnabled := true,
    Compile / run / fork := true,
    scalacOptions := scalacOptions.value
      .filterNot(_ == "-Xfatal-warnings") :+ "-Werror"
  )
