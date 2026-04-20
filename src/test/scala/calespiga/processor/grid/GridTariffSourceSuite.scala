package calespiga.processor.grid

import munit.CatsEffectSuite
import cats.effect.{IO, Ref}
import cats.effect.testkit.TestControl
import calespiga.model.GridTariff
import java.time.ZoneId
import scala.concurrent.duration.*
import java.time.LocalDateTime

class GridTariffSourceSuite extends CatsEffectSuite {

  // TestControl starts at Unix epoch = 1970-01-01T00:00:00Z. We will advance to a known date with a well-defined tariff schedule to test the transitions.
  // The schedule is based on the Spanish electricity tariff system, which has three tariffs (Vall, Pla, Pic) that change at specific times of day.
  // For our tests, we will use the following schedule for the timezone "Europe/Madrid":
  // Tariff transitions from epoch:
  //   Fri 00:00 → Vall (immediately)
  //   Fri 08:00 → Pla  (after 8h)
  //   Fri 10:00 → Pic  (after 2h more)
  //   Fri 14:00 → Pla  (after 4h more)
  //   Fri 18:00 → Pic  (after 4h more)
  //   Fri 22:00 → Pla  (after 4h more)
  //   Sat 00:00 → Vall (after 2h more)
  //   Mon 08:00 → Pla  (after 32h more)
  private val timezone = ZoneId.of("Europe/Madrid")
  val timezoneOffset = LocalDateTime
    .of(2026, 4, 17, 0, 0)
    .atZone(timezone)
    .toInstant()
    .toEpochMilli()
    .millis // offset from epoch to our test start time

  private def executeWithOffset[A](program: IO[A]): IO[A] =
    TestControl.executeEmbed(IO.sleep(timezoneOffset) *> program)

  test("emits current tariff immediately on start") {
    val program = GridTariffSource(timezone).events
      .take(1)
      .compile
      .lastOrError
      .map(e => assertEquals(e.tariff, GridTariff.Vall))

    executeWithOffset(program)
  }

  test("emits correct tariff sequence across multiple transitions") {
    val program = GridTariffSource(timezone).events
      .take(8)
      .map(_.tariff)
      .compile
      .toList
      .map { tariffs =>
        assertEquals(
          tariffs,
          List(
            GridTariff.Vall, // Fri 00:00
            GridTariff.Pla, // Fri 08:00
            GridTariff.Pic, // Fri 10:00
            GridTariff.Pla, // Fri 14:00
            GridTariff.Pic, // Fri 18:00
            GridTariff.Pla, // Fri 22:00
            GridTariff.Vall, // Sat 00:00
            GridTariff.Pla // Mon 08:00
          )
        )
      }

    executeWithOffset(program)
  }

  test("does not emit Pla before the 8-hour transition boundary") {
    // The stream fiber starts at t≈0 and registers its first sleep until t=8h.
    // Sleeping 1ms lets the stream run its initial eval; then we advance time
    // just under 8h, confirming no Pla has been emitted, before crossing the boundary.
    val program = for {
      emitted <- Ref.of[IO, List[GridTariff]](List.empty)
      _ <- GridTariffSource(timezone).events
        .evalTap(e => emitted.update(_ :+ e.tariff))
        .take(2)
        .compile
        .drain
        .start
      _ <- IO.sleep(1.milli) // let stream emit Vall and register 8h sleep
      before <- emitted.get
      _ <- IO.sleep(
        7.hours + 59.minutes
      ) // total ≈ 7h59m+1ms; stream 8h sleep not expired
      nearBound <- emitted.get
      _ <- IO.sleep(1.minute) // total ≈ 8h0m+1ms; crosses 8h → Pla emitted
      after <- emitted.get
    } yield {
      assertEquals(before, List(GridTariff.Vall))
      assertEquals(nearBound, List(GridTariff.Vall))
      assertEquals(after, List(GridTariff.Vall, GridTariff.Pla))
    }

    executeWithOffset(program)
  }

  test("does not emit Pic before the 10-hour transition boundary") {
    val program = for {
      emitted <- Ref.of[IO, List[GridTariff]](List.empty)
      _ <- GridTariffSource(timezone).events
        .evalTap(e => emitted.update(_ :+ e.tariff))
        .take(3)
        .compile
        .drain
        .start
      _ <- IO.sleep(1.milli) // let initial emit run
      _ <- IO.sleep(8.hours) // cross 8h → Pla emitted
      afterPla <- emitted.get
      _ <- IO.sleep(
        1.hour + 59.minutes
      ) // total ≈ 9h59m+1ms; 10h sleep not expired
      nearBound <- emitted.get
      _ <- IO.sleep(1.minute) // total ≈ 10h0m+1ms; crosses 10h → Pic emitted
      after <- emitted.get
    } yield {
      assertEquals(afterPla, List(GridTariff.Vall, GridTariff.Pla))
      assertEquals(nearBound, List(GridTariff.Vall, GridTariff.Pla))
      assertEquals(after, List(GridTariff.Vall, GridTariff.Pla, GridTariff.Pic))
    }

    executeWithOffset(program)
  }
}
