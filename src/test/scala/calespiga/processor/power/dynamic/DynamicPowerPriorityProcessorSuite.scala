package calespiga.processor.power.dynamic

import munit.FunSuite
import calespiga.model.{State, Event}
import calespiga.processor.heater.HeaterDynamicPowerConsumer
import java.time.Instant
import com.softwaremill.quicklens.*

class DynamicPowerPriorityProcessorSuite extends FunSuite {

  private val now = Instant.parse("2023-08-17T10:00:00Z")
  private val processor = DynamicPowerPriorityProcessor()

  private val heaterCode = HeaterDynamicPowerConsumer.consumerUniqueCode

  private def stateWithConsumers(consumers: Seq[String]): State =
    State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(consumers)

  test(
    "HeaterPowerPriorityChanged: swaps heater with element at target position"
  ) {
    val initialConsumers =
      Seq("consumer1", heaterCode, "consumer3", "consumer4")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 1)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq(heaterCode, "consumer1", "consumer3", "consumer4"),
      "Heater should be swapped to position 0"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: swaps heater from first to last position"
  ) {
    val initialConsumers =
      Seq(heaterCode, "consumer2", "consumer3", "consumer4")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 4)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer4", "consumer2", "consumer3", heaterCode),
      "Heater should be swapped to last position"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: swaps heater from middle to middle position"
  ) {
    val initialConsumers =
      Seq("consumer1", "consumer2", heaterCode, "consumer4", "consumer5")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 2)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", heaterCode, "consumer2", "consumer4", "consumer5"),
      "Heater should be swapped with consumer at position 1"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: does nothing when heater already at target position"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 2)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", heaterCode, "consumer3"),
      "Order should remain unchanged when swapping with itself"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: does nothing when heater not found in list"
  ) {
    val initialConsumers = Seq("consumer1", "consumer2", "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 2)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      initialConsumers,
      "Order should remain unchanged when heater not found"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles empty consumer list"
  ) {
    val state = stateWithConsumers(Seq.empty)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 1)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq.empty,
      "Empty list should remain unchanged"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles single element list with heater"
  ) {
    val initialConsumers = Seq(heaterCode)
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 1)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq(heaterCode),
      "Single element list should remain unchanged"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles two element list"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode)
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 1)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq(heaterCode, "consumer1"),
      "Elements should be swapped in two-element list"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: swaps correctly when heater at last position"
  ) {
    val initialConsumers =
      Seq("consumer1", "consumer2", "consumer3", heaterCode)
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 2)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", heaterCode, "consumer3", "consumer2"),
      "Heater from last position should swap correctly with middle position"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: swaps with adjacent element forward"
  ) {
    val initialConsumers =
      Seq("consumer1", heaterCode, "consumer3", "consumer4")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 3)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", "consumer3", heaterCode, "consumer4"),
      "Heater should swap with adjacent element forward"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: swaps with adjacent element backward"
  ) {
    val initialConsumers =
      Seq("consumer1", "consumer2", heaterCode, "consumer4")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 2)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", heaterCode, "consumer2", "consumer4"),
      "Heater should swap with adjacent element backward"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "Non-DynamicPower event: returns state unchanged"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = 30f,
      linesPower = List.empty
    )

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState,
      state,
      "State should remain unchanged for non-DynamicPower events"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "StartupEvent: returns state unchanged"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event = Event.System.StartupEvent

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState,
      state,
      "State should remain unchanged for StartupEvent"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles invalid priority index (negative)"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 0)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      initialConsumers,
      "Order should remain unchanged for invalid negative priority"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles invalid priority index (too large)"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 10)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      initialConsumers,
      "Order should remain unchanged for invalid out-of-bounds priority"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles priority equal to list size"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 3)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", "consumer3", heaterCode),
      "Heater should be swapped to third position (last in the list)"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }

  test(
    "HeaterPowerPriorityChanged: handles priority greater than list size"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 4)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      initialConsumers,
      "Order should remain unchanged when priority is greater than list size"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }
  test(
    "HeaterPowerPriorityChanged: handles priority greater than list size"
  ) {
    val initialConsumers = Seq("consumer1", heaterCode, "consumer3")
    val state = stateWithConsumers(initialConsumers)
    val event =
      Event.Power.DynamicPower.HeaterPowerPriorityChanged(priority = 4)

    val (newState, actions) = processor.process(state, event, now)

    assertEquals(
      newState.powerManagement.dynamic.consumersOrder,
      initialConsumers,
      "Order should remain unchanged when priority is greater than list size"
    )
    assertEquals(actions, Set.empty, "No actions should be generated")
  }
}
