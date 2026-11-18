package calespiga.processor.utils

import java.time.Instant
import java.time.ZoneId

object EnergyCalculatorStub {

  def apply(
      calculateEnergyTodayStub: (
          Option[Instant],
          Instant,
          Int,
          Float,
          ZoneId
      ) => Float = (_, _, _, currentEnergy, _) => currentEnergy
  ): EnergyCalculator = new EnergyCalculator {
    override def calculateEnergyToday(
        lastChange: Option[Instant],
        currentTimestamp: Instant,
        previousPower: Int,
        currentEnergyToday: Float,
        zone: ZoneId
    ): Float =
      calculateEnergyTodayStub(
        lastChange,
        currentTimestamp,
        previousPower,
        currentEnergyToday,
        zone
      )
  }
}
