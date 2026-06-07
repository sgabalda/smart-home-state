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
      uiBlacklist: Ref[IO, Set[String]],
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
              val gridBl =
                if (!state.featureFlags.gridConnectionEnabled)
                  config.gridMqttTopic
                else Set.empty
              val carChargerBl =
                if (!state.featureFlags.carChargerManagementEnabled)
                  config.carChargerMqttTopic
                else Set.empty
              bl ++ heaterBl ++ stoveBl ++ gridBl ++ carChargerBl
            }
            .flatMap { _ =>
              uiBlacklist.update { bl =>
                val heaterBl =
                  if (!state.featureFlags.heaterManagementEnabled)
                    config.heaterUiNotification
                  else Set.empty
                val stoveBl =
                  if (!state.featureFlags.infraredStoveEnabled)
                    config.infraredStoveUiNotification
                  else Set.empty
                val gridBl =
                  if (!state.featureFlags.gridConnectionEnabled)
                    config.gridUiNotification
                  else Set.empty
                bl ++ heaterBl ++ stoveBl ++ gridBl
              }
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
                  ),
                  Action.SetUIItemValue(
                    config.setGridConnectionEnabledItem,
                    state.featureFlags.gridConnectionEnabled.toString
                  ),
                  Action.SetUIItemValue(
                    config.setCarChargerManagementItem,
                    state.featureFlags.carChargerManagementEnabled.toString
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

        case Event.FeatureFlagEvents.SetCarChargerManagement(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.carChargerMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.carChargerMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .as(
              (
                state
                  .modify(_.featureFlags.carChargerManagementEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info(
            "Car charger MQTT feature flag set to " + enable
          )

        case Event.FeatureFlagEvents.SetGridConnectionEnabled(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.gridMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.gridMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .flatMap(_ =>
              uiBlacklist.update { bl =>
                if (enable) bl -- config.gridUiNotification
                else bl ++ config.gridUiNotification
              }
            )
            .as(
              (
                state
                  .modify(_.featureFlags.gridConnectionEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info(
            "Grid connection MQTT feature flag set to " + enable
          )

        case _ =>
          IO.pure((state, Set.empty))
      }

    }

  }

  def apply(
      mqttBlacklist: Ref[IO, Set[String]],
      uiBlacklist: Ref[IO, Set[String]],
      config: FeatureFlagsConfig
  ): EffectfulProcessor = Impl(mqttBlacklist, uiBlacklist, config)
}
