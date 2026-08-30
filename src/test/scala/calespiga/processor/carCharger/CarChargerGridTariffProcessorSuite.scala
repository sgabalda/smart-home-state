package calespiga.processor.carCharger

import calespiga.model.{Action, BatteryChargeTariff, Event, State}
import calespiga.processor.ProcessorConfigHelper
import com.softwaremill.quicklens.*
import java.time.Instant
import munit.FunSuite

class CarChargerGridTariffProcessorSuite extends FunSuite {

  private val processor = CarChargerGridTariffProcessor(
    ProcessorConfigHelper.carCharger
  )
  private val now = Instant.parse("2024-01-01T10:00:00Z")
  private val item = ProcessorConfigHelper.carCharger.maxGridTariffItem

  test("max grid tariff change updates state") {
    val (state, actions) = processor.process(
      State(),
      Event.CarCharger.CarChargerMaxGridTariffChanged(
        BatteryChargeTariff.PlaAndVall
      ),
      now
    )

    assertEquals(
      state.carCharger.maxGridTariff,
      Some(BatteryChargeTariff.PlaAndVall)
    )
    assertEquals(actions, Set.empty[Action])
  }

  test("startup restores max grid tariff to the UI") {
    val state = State()
      .modify(_.carCharger.maxGridTariff)
      .setTo(Some(BatteryChargeTariff.Vall))

    val (_, actions) = processor.process(state, Event.System.StartupEvent, now)

    assertEquals(
      actions,
      Set[Action](Action.SetUIItemValue(item, "vall"))
    )
  }

  test("ignores unrelated events without modification") {
    val initialState = State()
      .modify(_.carCharger.maxGridTariff)
      .setTo(Some(BatteryChargeTariff.PlaAndVall))

    // Test various unrelated events that should not modify state or generate actions
    val powerEvent = Event.CarCharger.CarChargerPowerReported(1500f)
    val (stateAfterPower, actionsAfterPower) =
      processor.process(initialState, powerEvent, now)

    assertEquals(
      stateAfterPower.carCharger.maxGridTariff,
      initialState.carCharger.maxGridTariff
    )
    assertEquals(actionsAfterPower, Set.empty)

    // Test with another unrelated event
    val statusEvent = Event.CarCharger.CarChargerStatusReported(
      calespiga.model.CarChargerSignal.On
    )
    val (stateAfterStatus, actionsAfterStatus) =
      processor.process(initialState, statusEvent, now)

    assertEquals(
      stateAfterStatus.carCharger.maxGridTariff,
      initialState.carCharger.maxGridTariff
    )
    assertEquals(actionsAfterStatus, Set.empty)
  }
}
