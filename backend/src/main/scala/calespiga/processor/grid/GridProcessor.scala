package calespiga.processor.grid

import calespiga.config.GridConfig
import calespiga.processor.SingleProcessor

/** Aggregate processor for grid tariff and available-power state.
  */
object GridProcessor {

  def apply(
      config: GridConfig
  ): SingleProcessor =
    GridTariffProcessor(config)
      .andThen(GridAvailablePowerProcessor(config))
}
