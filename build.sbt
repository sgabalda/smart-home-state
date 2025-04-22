import org.typelevel.sbt.tpolecat.*

ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.4.0"

lazy val root = (project in file(".")).settings(
  name := "smart-home-state",
  libraryDependencies ++= Seq(
    // "core" module - IO, IOApp, schedulers
    // This pulls in the kernel and std modules automatically.
    "org.typelevel" %% "cats-effect" % "3.5.3",
    // concurrency abstractions and primitives (Concurrent, Sync, Async etc.)
    "org.typelevel" %% "cats-effect-kernel" % "3.5.3",
    // standard "effect" library (Queues, Console, Random etc.)
    "org.typelevel" %% "cats-effect-std" % "3.5.3",
    "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test,
    //quicklens
    "com.softwaremill.quicklens" %% "quicklens" % "1.9.12")
)
