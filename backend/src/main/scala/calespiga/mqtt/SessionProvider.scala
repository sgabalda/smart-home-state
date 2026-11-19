package calespiga.mqtt

import calespiga.config.MqttConfig
import cats.effect.IO
import cats.effect.ResourceIO
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import net.sigusr.mqtt.api.*
import net.sigusr.mqtt.api.RetryConfig.Custom
import retry.RetryPolicies
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

object SessionProvider {

  def apply(
      config: MqttConfig,
      clientIdSuffix: String
  ): ResourceIO[Session[IO]] = {
    val retryConfig: Custom[IO] = Custom[IO](
      RetryPolicies
        .exponentialBackoff[IO](100.milliseconds)
        .join(RetryPolicies.fullJitter[IO](FiniteDuration(2, SECONDS)))
    )
    val transportConfig =
      TransportConfig[IO](
        Host.fromString(config.host).get,
        Port.fromString(config.port.toString).get,
        // TLS support looks like
        // 8883,
        // tlsConfig = Some(TLSConfig(TLSContextKind.System)),
        retryConfig = retryConfig,
        traceMessages = config.traceMessages
      )
    val sessionConfig =
      SessionConfig(
        config.clientId + clientIdSuffix,
        cleanSession = config.cleanSession,
        keepAlive = config.keepAlive
      )

    Session[IO](transportConfig, sessionConfig)
  }

}
