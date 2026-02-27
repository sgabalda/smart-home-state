package calespiga.processor

import calespiga.model.{Action, Event, State}
import calespiga.processor.heater.HeaterProcessor
import java.time.ZoneId
import cats.effect.IO
import cats.effect.Ref
import calespiga.processor.temperatures.TemperaturesProcessor
import calespiga.processor.power.PowerProcessor
import calespiga.processor.infraredStove.InfraredStoveProcessor

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

    private val compiledProcessors = processors match
      case head :: next =>
        Some(next.foldLeft(head) { (acc, processor) =>
          acc.andThen(processor)
        })
      case Nil => None

    override def process(
        state: State,
        event: Event
    ): IO[(State, Set[Action])] = compiledProcessors match
      case Some(processor) =>
        processor.process(state, event.data, event.timestamp)
      case None => IO.pure((state, Set.empty))
  }

  // private to package to ease testing but ensure the list of processors is configured here
  private[processor] def apply(
      processors: EffectfulProcessor*
  ): StateProcessor = Impl(processors.toList)

  // private to package to ease testing but ensure there are no duplicates in the dynamic consumer codes
  private[processor] def allButPower(
      config: calespiga.config.ProcessorConfig,
      mqttBlacklist: Ref[IO, Set[String]],
      zoneId: ZoneId
  ): List[EffectfulProcessor] = List(
    TemperaturesProcessor(
      config.temperatureFans,
      config.offlineDetector,
      config.syncDetector
    ).toEffectful,
    HeaterProcessor(
      config.heater,
      zoneId,
      config.offlineDetector,
      config.syncDetector
    ).toEffectful,
    InfraredStoveProcessor(
      config.infraredStove,
      zoneId,
      config.offlineDetector,
      config.syncDetector
    ).toEffectful,
    FeatureFlagsProcessor(mqttBlacklist, config.featureFlags)
  )

  def apply(
      config: calespiga.config.ProcessorConfig,
      mqttBlacklist: Ref[IO, Set[String]],
      zoneId: ZoneId
  ): StateProcessor = {

    val allButPowerProcessors = allButPower(config, mqttBlacklist, zoneId)

    val power = PowerProcessor(
      config.power,
      zoneId,
      allButPowerProcessors.flatMap(_.dynamicPowerConsumer).toSet
    )

    this.apply(
      (allButPowerProcessors :+ power.toEffectful)*
    )

  }

}
