package calespiga.processor.power

import calespiga.processor.EffectfulProcessor
import calespiga.config.PowerProcessorConfig
import java.time.ZoneId
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer
import calespiga.processor.power.dynamic.DynamicPowerPriorityProcessor

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
