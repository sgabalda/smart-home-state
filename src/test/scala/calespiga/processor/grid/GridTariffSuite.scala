package calespiga.processor.grid

import munit.FunSuite
import calespiga.model.GridTariff
import java.time.{Instant, ZoneId}

class GridTariffSuite extends FunSuite {

  private val utc = ZoneId.of("UTC")

  // 2024-01-01 is a Monday
  private def instant(dayOffset: Int, hour: Int): Instant =
    Instant.parse(f"2024-01-0${1 + dayOffset}T${hour}%02d:00:00Z")

  // Monday = offset 0, Tuesday = 1, ..., Sunday = 6
  test("weekday 00:00 is Vall") {
    assertEquals(GridTariff.at(instant(0, 0), utc), GridTariff.Vall)
  }

  test("weekday 07:59 is Vall") {
    val i = Instant.parse("2024-01-01T07:59:59Z")
    assertEquals(GridTariff.at(i, utc), GridTariff.Vall)
  }

  test("weekday 08:00 is Pla") {
    assertEquals(GridTariff.at(instant(0, 8), utc), GridTariff.Pla)
  }

  test("weekday 10:00 is Pic") {
    assertEquals(GridTariff.at(instant(0, 10), utc), GridTariff.Pic)
  }

  test("weekday 14:00 is Pla") {
    assertEquals(GridTariff.at(instant(0, 14), utc), GridTariff.Pla)
  }

  test("weekday 18:00 is Pic") {
    assertEquals(GridTariff.at(instant(0, 18), utc), GridTariff.Pic)
  }

  test("weekday 22:00 is Pla") {
    assertEquals(GridTariff.at(instant(0, 22), utc), GridTariff.Pla)
  }

  test("Saturday is Vall") {
    // 2024-01-06 is Saturday
    val sat = Instant.parse("2024-01-06T12:00:00Z")
    assertEquals(GridTariff.at(sat, utc), GridTariff.Vall)
  }

  test("Sunday is Vall") {
    val sun = Instant.parse("2024-01-07T20:00:00Z")
    assertEquals(GridTariff.at(sun, utc), GridTariff.Vall)
  }

  test("nextChangeInstant from weekday Vall points to 08:00 same day") {
    val now = Instant.parse("2024-01-01T05:00:00Z") // Monday 05:00 = Vall
    val next = GridTariff.nextChangeInstant(now, utc)
    assertEquals(next, Instant.parse("2024-01-01T08:00:00Z"))
  }

  test("nextChangeInstant from weekday Pla (08:00) points to 10:00") {
    val now = Instant.parse("2024-01-01T09:00:00Z") // Monday 09:00 = Pla
    val next = GridTariff.nextChangeInstant(now, utc)
    assertEquals(next, Instant.parse("2024-01-01T10:00:00Z"))
  }

  test("nextChangeInstant from Saturday Vall points to Monday 08:00") {
    val now = Instant.parse("2024-01-06T10:00:00Z") // Saturday 10:00 = Vall
    val next = GridTariff.nextChangeInstant(now, utc)
    assertEquals(next, Instant.parse("2024-01-08T08:00:00Z")) // Monday 08:00
  }

  test("nextChangeInstant from Friday 22:00 Pla points to Saturday 00:00") {
    val now = Instant.parse("2024-01-05T23:00:00Z") // Friday 23:00 = Pla
    val next = GridTariff.nextChangeInstant(now, utc)
    assertEquals(
      next,
      Instant.parse("2024-01-06T00:00:00Z")
    ) // Saturday 00:00 = Vall
  }

  // Daylight saving time tests using Europe/Madrid (CET/CEST)
  // Spring forward 2024: March 31 at 02:00 CET (UTC+1) → 03:00 CEST (UTC+2) — Sunday loses 1h
  // Fall back   2024: October 27 at 03:00 CEST (UTC+2) → 02:00 CET (UTC+1) — Sunday gains 1h

  private val madrid = ZoneId.of("Europe/Madrid")

  test(
    "spring-forward: next change from Saturday Vall resolves to Monday 08:00 CEST"
  ) {
    // Saturday March 30, 2024 22:00 CET (+01:00) = 2024-03-30T21:00:00Z
    val now = Instant.parse("2024-03-30T21:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    // Monday April 1 is already in CEST (+02:00), so 08:00 CEST = 06:00 UTC
    assertEquals(next, Instant.parse("2024-04-01T06:00:00Z"))
  }

  test("spring-forward: delay to Monday 08:00 is 33 hours, not the naive 34") {
    // Without DST awareness the wall-clock span Sat 22:00 → Mon 08:00 looks like 34 h.
    // The spring-forward on Sunday steals 1 h, so the actual elapsed time is 33 h.
    // IO.sleep uses this Duration, so it must reflect the real physical elapsed time.
    val now = Instant.parse("2024-03-30T21:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    assertEquals(
      java.time.Duration.between(now, next).toSeconds(),
      java.time.Duration.ofHours(33).toSeconds()
    )
  }

  test(
    "spring-forward: next change from spring-forward Sunday Vall points to Monday 08:00 CEST"
  ) {
    // Sunday March 31, 2024 10:00 CEST (+02:00) = 2024-03-31T08:00:00Z — already in summer time
    val now = Instant.parse("2024-03-31T08:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    assertEquals(next, Instant.parse("2024-04-01T06:00:00Z"))
  }

  test(
    "fall-back: next change from Saturday Vall resolves to Monday 08:00 CET"
  ) {
    // Saturday October 26, 2024 22:00 CEST (+02:00) = 2024-10-26T20:00:00Z
    val now = Instant.parse("2024-10-26T20:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    // Monday October 28 is in CET (+01:00), so 08:00 CET = 07:00 UTC
    assertEquals(next, Instant.parse("2024-10-28T07:00:00Z"))
  }

  test("fall-back: delay to Monday 08:00 is 35 hours, not the naive 34") {
    // Without DST awareness the wall-clock span Sat 22:00 → Mon 08:00 looks like 34 h.
    // The fall-back on Sunday adds 1 h, so the actual elapsed time is 35 h.
    val now = Instant.parse("2024-10-26T20:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    assertEquals(
      java.time.Duration.between(now, next).toSeconds,
      java.time.Duration.ofHours(35).toSeconds
    )
  }

  test(
    "fall-back: next change from fall-back Sunday Vall points to Monday 08:00 CET"
  ) {
    // Sunday October 27, 2024 10:00 CET (+01:00) = 2024-10-27T09:00:00Z — already in winter time
    val now = Instant.parse("2024-10-27T09:00:00Z")
    val next = GridTariff.nextChangeInstant(now, madrid)
    assertEquals(next, Instant.parse("2024-10-28T07:00:00Z"))
  }
}
