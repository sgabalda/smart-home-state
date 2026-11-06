package calespiga.processor

import calespiga.config.FeatureFlagsConfig
import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.Event.EventData
import calespiga.model.State
import cats.effect.IO
import cats.effect.Ref
import com.softwaremill.quicklens.*
import java.time.Instant
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
              val withGreyWater =
                if (state.featureFlags.greyWaterEnabled) bl
                else bl ++ config.greyWaterMqttTopic
              if (state.featureFlags.gardenWateringEnabled) withGreyWater
              else withGreyWater ++ config.gardenWateringMqttTopic
            }
            .as(
              (
                state,
                Set[Action](
                  Action.SetUIItemValue(
                    config.setGreyWaterEnabledItem,
                    state.featureFlags.greyWaterEnabled.toString
                  ),
                  Action.SetUIItemValue(
                    config.setGardenWateringEnabledItem,
                    state.featureFlags.gardenWateringEnabled.toString
                  )
                )
              )
            ) <* logger.info("Feature flags initialized on startup")

        case Event.FeatureFlagEvents.SetGreyWaterEnabled(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.greyWaterMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.greyWaterMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .as(
              (
                state
                  .modify(_.featureFlags.greyWaterEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info(
            "Grey water MQTT feature flag set to " + enable
          )

        case Event.FeatureFlagEvents.SetGardenWateringEnabled(enable) =>
          val modifier = if (enable) { (bl: Set[String]) =>
            bl -- config.gardenWateringMqttTopic
          } else { (bl: Set[String]) =>
            bl ++ config.gardenWateringMqttTopic
          }
          mqttBlacklist
            .update(modifier)
            .as(
              (
                state
                  .modify(_.featureFlags.gardenWateringEnabled)
                  .setTo(enable),
                Set.empty
              )
            ) <* logger.info(
            "Garden watering MQTT feature flag set to " + enable
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
