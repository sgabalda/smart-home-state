ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.7.3"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "smart-home-state",
    libraryDependencies ++= Dependencies.dependencies ++ Dependencies.testing,
    Global / semanticdbEnabled := true,
    Compile / run / fork := true
  )
