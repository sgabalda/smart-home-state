package calespiga.processor.power

import calespiga.processor.SingleProcessor
import calespiga.config.PowerAvailableProcessorConfig
import java.time.ZoneId
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer

object PowerProcessor {

  def apply(
      config: PowerAvailableProcessorConfig,
      zoneId: ZoneId,
      dynamicConsumers: Set[DynamicPowerConsumer]
  ): SingleProcessor =
    PowerAvailableProcessor(config, zoneId).andThen(
      DynamicPowerProcessor(DynamicConsumerOrderer(), dynamicConsumers)
    )
}
