package calespiga.processor

import calespiga.model.{Event, State, Action}
import calespiga.model.Event.EventData
import java.time.Instant
import com.softwaremill.quicklens.*
import cats.effect.{IO, Ref}
import calespiga.config.FeatureFlagsConfig

object FeatureFlagsProcessor {

  private case class Impl(
      mqttBlacklist: Ref[IO, Set[String]],
      config: FeatureFlagsConfig
  ) extends EffectfulProcessor {

    override def process(
        state: State,
        eventData: EventData,
        timestamp: Instant
    ): IO[(State, Set[Action])] = {
      val (newState, blackListModifier): (State, Set[String] => Set[String]) =
        eventData match {
          case Event.FeatureFlagEvents.SetFanManagement(enable) =>
            val modifier = if (enable) { (bl: Set[String]) =>
              bl -- config.temperaturesMqttTopic
            } else { (bl: Set[String]) =>
              bl ++ config.temperaturesMqttTopic
            }
            (
              state.modify(_.featureFlags.fanManagementEnabled).setTo(enable),
              modifier
            )

          case Event.FeatureFlagEvents.SetHeaterManagement(enable) =>
            val modifier = if (enable) { (bl: Set[String]) =>
              bl -- config.heaterMqttTopic
            } else { (bl: Set[String]) =>
              bl ++ config.heaterMqttTopic
            }
            (
              state
                .modify(_.featureFlags.heaterManagementEnabled)
                .setTo(enable),
              modifier
            )

          case _ =>
            (state, identity[Set[String]])
        }

      mqttBlacklist.update(blackListModifier).as((state, Set.empty))
    }

  }

  def apply(
      mqttBlacklist: Ref[IO, Set[String]],
      config: FeatureFlagsConfig
  ): EffectfulProcessor = Impl(mqttBlacklist, config)
}
