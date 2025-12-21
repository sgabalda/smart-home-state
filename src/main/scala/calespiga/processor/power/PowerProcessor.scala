package calespiga.processor.power

import calespiga.processor.SingleProcessor
import calespiga.config.PowerProcessorConfig
import java.time.ZoneId
import calespiga.processor.power.dynamic.DynamicConsumerOrderer
import calespiga.processor.power.dynamic.DynamicPowerConsumer

object PowerProcessor {

  def apply(
      config: PowerProcessorConfig,
      zoneId: ZoneId,
      dynamicConsumers: Set[DynamicPowerConsumer]
  ): SingleProcessor =
    PowerAvailableProcessor(config.powerAvailable, zoneId).andThen(
      DynamicPowerProcessor(
        DynamicConsumerOrderer(),
        dynamicConsumers,
        config.dynamicPower
      )
    )
}
