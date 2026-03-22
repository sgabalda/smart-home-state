package calespiga.processor.battery

import munit.FunSuite
import calespiga.model._
import java.time.Instant

class BatteryAlertOnLowSuite extends FunSuite {

  private val now = Instant.parse("2024-01-01T10:00:00Z")

  private def processor() =
    BatteryAlertOnLowProcessor()

  val baseState = State()

  test("Does send notification on Battery low event") {
    val p = processor()

    val (newState, actions) = p.process(
      baseState,
      Event.Battery.BatteryStatusReported(BatteryStatus.Low),
      now
    )

    assertEquals(newState, baseState)
    assertEquals(
      actions,
      Set[Action](
        Action.SendNotification(
          BatteryAlertOnLowProcessor.NOTIFICATION_ID,
          BatteryAlertOnLowProcessor.NOTIFICATION_MESSAGE,
          None
        )
      )
    )
  }

  test("Does NOT send notification on Battery medium event") {
    val p = processor()

    val (newState, actions) = p.process(
      baseState,
      Event.Battery.BatteryStatusReported(BatteryStatus.Medium),
      now
    )

    assertEquals(newState, baseState)
    assertEquals(
      actions,
      Set.empty[Action]
    )
  }

  test("Does send notification on Battery high event") {
    val p = processor()

    val (newState, actions) = p.process(
      baseState,
      Event.Battery.BatteryStatusReported(BatteryStatus.High),
      now
    )

    assertEquals(newState, baseState)
    assertEquals(
      actions,
      Set.empty[Action]
    )
  }

}
