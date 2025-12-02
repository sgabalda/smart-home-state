package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.processor.heater.HeaterProcessor
import java.time.ZoneId
import cats.effect.IO
import cats.effect.Ref
import calespiga.processor.temperatures.TemperaturesProcessor
import calespiga.processor.power.PowerAvailableProcessor

trait StateProcessor {
  def process(
      state: State,
      event: Event
  ): IO[(State, Set[Action])]
}

object StateProcessor {

  private final case class Impl(
      processors: List[EffectfulProcessor]
  ) extends StateProcessor {
    override def process(
        state: State,
        event: Event
    ): IO[(State, Set[Action])] = {
      processors.foldLeft(IO((state, Set.empty[Action]))) {
        case (acc, processor) =>
          acc.flatMap { case (currentState, currentActions) =>
            processor.process(currentState, event.data, event.timestamp).map {
              case (newState, newActions) =>
                (newState, currentActions ++ newActions)
            }
          }

      }
    }
  }

  def apply(
      processors: EffectfulProcessor*
  ): StateProcessor = Impl(processors.toList)

  def apply(
      config: calespiga.config.ProcessorConfig,
      mqttBlacklist: Ref[IO, Set[String]]
  ): StateProcessor =
    this.apply(
      TemperaturesProcessor(
        config.temperatureFans,
        config.offlineDetector,
        config.syncDetector
      ).toEffectful,
      HeaterProcessor(
        config.heater,
        ZoneId.systemDefault(),
        config.offlineDetector,
        config.syncDetector
      ).toEffectful,
      PowerAvailableProcessor(config.powerAvailable).toEffectful,
      FeatureFlagsProcessor(mqttBlacklist, config.featureFlags)
    )

}
