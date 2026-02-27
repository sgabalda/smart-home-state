package calespiga.processor.utils

import munit.FunSuite
import java.time.Instant
import java.time.ZoneId

class EnergyCalculatorSuite extends FunSuite {

  private val zone: ZoneId = ZoneId.of("UTC")
  private val calculator = EnergyCalculator()

  test(
    "calculateEnergyToday accumulates energy when on the same day"
  ) {
    val now = Instant.parse("2023-08-17T10:00:00Z")
    val oneHourAgo = now.minusSeconds(3600)
    val currentEnergyToday = 1000f
    val power = 500 // watts

    val result = calculator.calculateEnergyToday(
      Some(oneHourAgo),
      now,
      power,
      currentEnergyToday,
      zone
    )

    // Energy for 1 hour at 500W = 500Wh
    // Total should be 1000 + 500 = 1500
    assertEqualsDouble(result, 1500f, 0.1f)
  }

  test(
    "calculateEnergyToday resets energy when crossing to a new day"
  ) {
    // Use specific dates to ensure we can control same-day vs different-day
    val todayMorning = Instant.parse("2023-08-17T08:00:00Z")
    val todayNoon =
      Instant.parse("2023-08-17T12:00:00Z") // 4 hours later, same day

    val yesterdayMorning = Instant.parse("2023-08-16T08:00:00Z")
    val todayMorning2 = Instant.parse(
      "2023-08-17T12:00:00Z"
    ) // 4 hours later (in terms of duration), but different day

    val currentEnergyToday = 10000f
    val power = 500 // watts

    // Calculate for same day: 8am to noon = 4 hours
    val resultSameDay = calculator.calculateEnergyToday(
      Some(todayMorning),
      todayNoon,
      power,
      currentEnergyToday,
      zone
    )

    // Calculate for different day: yesterday 8am to today noon
    // Even though this is more than 4 hours, what matters is that it's a DIFFERENT day
    val resultDifferentDay = calculator.calculateEnergyToday(
      Some(yesterdayMorning),
      todayMorning2,
      power,
      currentEnergyToday,
      zone
    )

    // Energy for 4 hours at 500W = 2000Wh
    val expectedEnergyForPeriod = 2000f

    // Same day should accumulate: currentEnergyToday + calculated energy
    assertEqualsDouble(
      resultSameDay,
      currentEnergyToday + expectedEnergyForPeriod,
      0.1f,
      "Same day should accumulate energy"
    )

    // Different day should reset: just the calculated energy (no accumulation)
    // From yesterday morning to today morning is 24 hours + 4 more hours = 28 hours
    val expectedEnergyDifferentDay = 500f * 28f // 28 hours at 500W
    assertEqualsDouble(
      resultDifferentDay,
      expectedEnergyDifferentDay,
      0.1f,
      "Different day should reset and only return calculated energy for the period"
    )

    // Key assertion: resultDifferentDay should NOT include currentEnergyToday
    assert(
      resultDifferentDay < currentEnergyToday + expectedEnergyDifferentDay,
      "Different day result should not include accumulated energy from previous day"
    )
  }

  test(
    "calculateEnergyToday handles None lastChange by using current timestamp"
  ) {
    val now = Instant.parse("2023-08-17T10:00:00Z")
    val currentEnergyToday = 1000f
    val power = 500

    val result = calculator.calculateEnergyToday(
      None,
      now,
      power,
      currentEnergyToday,
      zone
    )

    // No time has elapsed, so energy should remain the same
    assertEqualsDouble(result, currentEnergyToday, 0.1f)
  }

  test(
    "calculateEnergyToday calculates correct energy for 2 hours at 1000W"
  ) {
    val now = Instant.parse("2023-08-17T12:00:00Z")
    val twoHoursAgo = now.minusSeconds(7200)
    val currentEnergyToday = 0f
    val power = 1000 // watts

    val result = calculator.calculateEnergyToday(
      Some(twoHoursAgo),
      now,
      power,
      currentEnergyToday,
      zone
    )

    // Energy for 2 hours at 1000W = 2000Wh
    assertEqualsDouble(result, 2000f, 0.1f)
  }

  test(
    "calculateEnergyToday calculates correct energy for 30 minutes at 2000W"
  ) {
    val now = Instant.parse("2023-08-17T10:30:00Z")
    val thirtyMinutesAgo = now.minusSeconds(1800)
    val currentEnergyToday = 500f
    val power = 2000 // watts

    val result = calculator.calculateEnergyToday(
      Some(thirtyMinutesAgo),
      now,
      power,
      currentEnergyToday,
      zone
    )

    // Energy for 0.5 hour at 2000W = 1000Wh
    // Total = 500 + 1000 = 1500
    assertEqualsDouble(result, 1500f, 0.1f)
  }

  test("calculateEnergyToday handles zero power") {
    val now = Instant.parse("2023-08-17T10:00:00Z")
    val oneHourAgo = now.minusSeconds(3600)
    val currentEnergyToday = 1000f
    val power = 0 // watts

    val result = calculator.calculateEnergyToday(
      Some(oneHourAgo),
      now,
      power,
      currentEnergyToday,
      zone
    )

    // No additional energy consumed, should remain 1000
    assertEqualsDouble(result, 1000f, 0.1f)
  }
}
