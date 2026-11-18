package calespiga.processor.utils

import java.time.Instant
import java.time.ZoneId

/** Calculates energy consumption over a time period.
  *
  * This utility is used to accumulate energy consumption for devices that
  * report their power status periodically.
  */
trait EnergyCalculator {

  /** Calculates the new energy total for today based on the previous state and
    * current update.
    *
    * @param lastChange
    *   The timestamp of the last status change, or None if this is the first
    *   update
    * @param currentTimestamp
    *   The timestamp of the current status update
    * @param previousPower
    *   The power consumption (in watts) during the period since lastChange
    * @param currentEnergyToday
    *   The accumulated energy consumption (in Wh) for today so far
    * @param zone
    *   The time zone used to determine if we're still on the same day
    * @return
    *   The new accumulated energy (in Wh) for today. If the current timestamp
    *   is on a different day than lastChange, the accumulated energy is reset
    *   and only the energy for the period since lastChange is returned.
    */
  def calculateEnergyToday(
      lastChange: Option[Instant],
      currentTimestamp: Instant,
      previousPower: Int,
      currentEnergyToday: Float,
      zone: ZoneId
  ): Float
}

object EnergyCalculator {

  def apply(): EnergyCalculator = Impl

  private object Impl extends EnergyCalculator {

    override def calculateEnergyToday(
        lastChange: Option[Instant],
        currentTimestamp: Instant,
        previousPower: Int,
        currentEnergyToday: Float,
        zone: ZoneId
    ): Float = {
      val lastEnergyUpdate = lastChange.getOrElse(currentTimestamp)
      val sameDay =
        lastEnergyUpdate.atZone(zone).toLocalDate == currentTimestamp
          .atZone(zone)
          .toLocalDate

      // Calculate energy consumed in the last period (in Wh)
      // Formula: time (ms) * power (W) / (1000 ms/s) / (3600 s/h) = Wh
      val energyLastPeriod =
        lastEnergyUpdate
          .until(currentTimestamp)
          .toMillis * previousPower / 1000f / 3600f

      // If same day, accumulate; otherwise reset to just the last period
      if (sameDay) currentEnergyToday + energyLastPeriod
      else energyLastPeriod
    }
  }
}
