package calespiga.processor.power

import calespiga.config.PowerProcessorConfig
import calespiga.processor.EffectfulProcessor
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerPriorityProcessor
import java.time.ZoneId

object PowerProcessor {

  def apply(
      config: PowerProcessorConfig,
      zoneId: ZoneId,
      dynamicConsumers: Set[DynamicPowerConsumer],
      manager: calespiga.processor.grid.GridConnectionManager
  ): EffectfulProcessor =
    PowerAvailableProcessor(config.powerAvailable, zoneId).toEffectful.andThen(
      DynamicPowerProcessor(
        DynamicConsumerOrderer(),
        dynamicConsumers,
        config.dynamicPower,
        manager
      ).andThen(DynamicPowerPriorityProcessor().toEffectful)
    )
}
