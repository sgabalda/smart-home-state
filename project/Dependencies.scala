import sbt.*

object Dependencies {
  object Versions {
    val cats_effect = "3.6.3"

    val circe = "0.14.14"

    val fs2 = "3.12.2"

    val fs2_mqtt = "1.0.1"

    val janino = "3.1.12"

    val log4cats = "2.7.0"

    val logback = "1.5.18"

    val munit = "2.1.0"

    val pureconfig = "0.17.9"

    val quicklens = "1.9.12"

    val sttp = "4.0.11"
  }

  val dependencies: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-effect" % Versions.cats_effect,
    "org.typelevel" %% "cats-effect-kernel" % Versions.cats_effect,
    "org.typelevel" %% "cats-effect-std" % Versions.cats_effect,
    "io.circe" %% "circe-core" % Versions.circe, // Core functionality
    "io.circe" %% "circe-generic" % Versions.circe, // Auto-derivation for case classes
    "io.circe" %% "circe-parser" % Versions.circe,
    "co.fs2" %% "fs2-io" % Versions.fs2,
    "co.fs2" %% "fs2-scodec" % Versions.fs2,
    "net.sigusr" %% "fs2-mqtt" % Versions.fs2_mqtt,
    "org.codehaus.janino" % "janino" % Versions.janino,
    "org.typelevel" %% "log4cats-core" % Versions.log4cats,
    "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats,
    "ch.qos.logback" % "logback-classic" % Versions.logback,
    "com.github.pureconfig" %% "pureconfig-core" % Versions.pureconfig,
    "com.softwaremill.quicklens" %% "quicklens" % Versions.quicklens,
    "com.softwaremill.sttp.client4" %% "core" % Versions.sttp,
    "com.softwaremill.sttp.client4" %% "cats" % Versions.sttp,
    "com.softwaremill.sttp.client4" %% "fs2" % Versions.sttp
  )

  val testing: Seq[ModuleID] = Seq(
    "org.typelevel" %% "munit-cats-effect" % Versions.munit,
    "org.typelevel" %% "cats-effect-testkit" % Versions.cats_effect
  ).map(_ % Test)
}
