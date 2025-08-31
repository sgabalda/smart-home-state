ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "smart-home-state",
    libraryDependencies ++= Dependencies.dependencies ++ Dependencies.testing,
    Global / semanticdbEnabled := true
  )
