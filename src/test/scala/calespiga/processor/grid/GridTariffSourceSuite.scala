package calespiga.processor.grid

import munit.CatsEffectSuite
import cats.effect.{IO, Ref}
import cats.effect.testkit.TestControl
import calespiga.model.GridTariff
import java.time.ZoneId
import scala.concurrent.duration.*

class GridTariffSourceSuite extends CatsEffectSuite {

  // TestControl starts at Unix epoch = 1970-01-01T00:00:00Z = Thursday 00:00 = Vall
  // Tariff transitions from epoch:
  //   Thu 00:00 → Vall (immediately)
  //   Thu 08:00 → Pla  (after 8h)
  //   Thu 10:00 → Pic  (after 2h more)
  //   Thu 14:00 → Pla  (after 4h more)
  //   Thu 18:00 → Pic  (after 4h more)
  //   Thu 22:00 → Pla  (after 4h more)
  //   Fri 00:00 → Vall (after 2h more)
  private val utc = ZoneId.of("UTC")

  test("emits current tariff immediately on start") {
    val program = GridTariffSource(utc).events
      .take(1)
      .compile
      .lastOrError
      .map(e => assertEquals(e.tariff, GridTariff.Vall))

    TestControl.executeEmbed(program)
  }

  test("emits correct tariff sequence across multiple transitions") {
    val program = GridTariffSource(utc).events
      .take(7)
      .map(_.tariff)
      .compile
      .toList
      .map { tariffs =>
        assertEquals(
          tariffs,
          List(
            GridTariff.Vall, // Thu 00:00
            GridTariff.Pla, // Thu 08:00
            GridTariff.Pic, // Thu 10:00
            GridTariff.Pla, // Thu 14:00
            GridTariff.Pic, // Thu 18:00
            GridTariff.Pla, // Thu 22:00
            GridTariff.Vall // Fri 00:00
          )
        )
      }

    TestControl.executeEmbed(program)
  }

  test("does not emit Pla before the 8-hour transition boundary") {
    // The stream fiber starts at t≈0 and registers its first sleep until t=8h.
    // Sleeping 1ms lets the stream run its initial eval; then we advance time
    // just under 8h, confirming no Pla has been emitted, before crossing the boundary.
    val program = for {
      emitted <- Ref.of[IO, List[GridTariff]](List.empty)
      _ <- GridTariffSource(utc).events
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

    TestControl.executeEmbed(program)
  }

  test("does not emit Pic before the 10-hour transition boundary") {
    val program = for {
      emitted <- Ref.of[IO, List[GridTariff]](List.empty)
      _ <- GridTariffSource(utc).events
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

    TestControl.executeEmbed(program)
  }
}
