ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.4.0"

lazy val root = (project in file(".")).settings(
  name := "smart-home-state",
  libraryDependencies ++= Dependencies.dependencies ++ Dependencies.testing
)
