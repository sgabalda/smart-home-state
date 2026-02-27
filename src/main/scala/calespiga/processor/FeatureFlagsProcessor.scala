package calespiga.processor

import calespiga.model.{Event, State, Action}
import calespiga.model.Event.EventData
import java.time.Instant
import com.softwaremill.quicklens.*
import cats.effect.{IO, Ref}
import calespiga.config.FeatureFlagsConfig
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object FeatureFlagsProcessor {

  private given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private case class Impl(
      mqttBlacklist: Ref[IO, Set[String]],
      config: FeatureFlagsConfig
  ) extends EffectfulProcessor {

    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {

      eventData match {
        case Event.System.StartupEvent =>
          mqttBlacklist
            .update { bl =>
              val heaterBl =
                if (!state.featureFlags.heaterManagementEnabled)
                  config.heaterMqttTopic
                else Set.empty
              val stoveBl =
                if (!state.featureFlags.infraredStoveEnabled)
                  config.infraredStoveMqttTopic
                else Set.empty
              bl ++ heaterBl ++ stoveBl
            }
            .as(
              (
                state,
                Set(
                  Action.SetUIItemValue(
                    config.setHeaterManagementItem,
                    state.featureFlags.heaterManagementEnabled.toString
                  ),
                  Action.SetUIItemValue(
                    config.setInfraredStoveEnabledItem,
                    state.featureFlags.infraredStoveEnabled.toString
                  )
                )
              )
            ) <* logger.info("Feature flags initialized on startup")

        case Event.FeatureFlagEvents.SetHeaterManagement(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.heaterMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.heaterMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .as(
              (
                state
                  .modify(_.featureFlags.heaterManagementEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info("Heater management feature flag set to " + enable)

        case Event.FeatureFlagEvents.SetInfraredStoveEnabled(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.infraredStoveMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.infraredStoveMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .as(
              (
                state
                  .modify(_.featureFlags.infraredStoveEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info(
            "Infrared stove MQTT feature flag set to " + enable
          )

        case _ =>
          IO.pure((state, Set.empty))
      }

    }

  }

  def apply(
      mqttBlacklist: Ref[IO, Set[String]],
      config: FeatureFlagsConfig
  ): EffectfulProcessor = Impl(mqttBlacklist, config)
}
