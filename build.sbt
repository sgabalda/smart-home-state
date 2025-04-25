import org.typelevel.sbt.tpolecat.*

ThisBuild / organization := "calespiga"
ThisBuild / scalaVersion := "3.4.0"

lazy val root = (project in file(".")).settings(
  name := "smart-home-state",
  libraryDependencies ++= Seq(
    // "core" module - IO, IOApp, schedulers
    // This pulls in the kernel and std modules automatically.
    "org.typelevel" %% "cats-effect" % "3.6.1",
    // concurrency abstractions and primitives (Concurrent, Sync, Async etc.)
    "org.typelevel" %% "cats-effect-kernel" % "3.6.1",
    // standard "effect" library (Queues, Console, Random etc.)
    "org.typelevel" %% "cats-effect-std" % "3.6.1",
    "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
    //quicklens
    "com.softwaremill.quicklens" %% "quicklens" % "1.9.12",
    // "fs2" for MQTT
    "net.sigusr" %% "fs2-mqtt" % "1.0.1",
    "co.fs2" %% "fs2-io" % "3.12.0",
    "co.fs2" %% "fs2-scodec" % "3.12.0",
    "com.github.pureconfig" %% "pureconfig-core" % "0.17.9"
  )
)
