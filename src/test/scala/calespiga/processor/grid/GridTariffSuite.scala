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
}
