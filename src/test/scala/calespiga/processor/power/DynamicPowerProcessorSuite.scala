package calespiga.processor.power

import munit.FunSuite
import calespiga.model.{State, Action, Event}
import calespiga.processor.power.dynamic.{
  DynamicConsumerOrdererStub,
  DynamicPowerConsumerStub
}
import calespiga.processor.power.dynamic.DynamicPowerConsumer.DynamicPowerResult
import java.time.Instant
import scala.collection.mutable.ListBuffer
import calespiga.processor.power.dynamic.Power
import calespiga.processor.ProcessorConfigHelper

class DynamicPowerProcessorSuite extends FunSuite {

  val now = Instant.parse("2023-08-17T10:00:00Z")

  val processorConfig = ProcessorConfigHelper.dynamicPowerProcessorConfig

  test(
    "DynamicPowerProcessor respects consumer ordering from DynamicConsumerOrderer"
  ) {
    val callOrder = ListBuffer.empty[String]

    val trackingConsumer1 = DynamicPowerConsumerStub(
      usePowerStub = (state, _, _) => {
        callOrder += "consumer1"
        DynamicPowerResult(state, Set.empty, Power.zero)
      }
    )
    val trackingConsumer2 = DynamicPowerConsumerStub(
      usePowerStub = (state, _, _) => {
        callOrder += "consumer2"
        DynamicPowerResult(state, Set.empty, Power.zero)
      }
    )
    val trackingConsumer3 = DynamicPowerConsumerStub(
      usePowerStub = (state, _, _) => {
        callOrder += "consumer3"
        DynamicPowerResult(state, Set.empty, Power.zero)
      }
    )

    val ordererWithTracking = DynamicConsumerOrdererStub(
      orderConsumersStub =
        (_, _) => Seq(trackingConsumer2, trackingConsumer1, trackingConsumer3)
    )

    val processor = DynamicPowerProcessor(
      ordererWithTracking,
      Set(trackingConsumer1, trackingConsumer2, trackingConsumer3),
      processorConfig
    )

    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = 30f,
      linesPower = List.empty
    )

    val (_, _) = processor.process(state, event, now)

    assertEquals(
      callOrder.toList,
      List("consumer2", "consumer1", "consumer3"),
      "Consumers should be called in the order returned by DynamicConsumerOrderer"
    )
  }

  test(
    "DynamicPowerProcessor distributes power correctly with decreasing amounts"
  ) {
    val powerOffered = ListBuffer.empty[Power]

    val consumer1 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(10f),
      usePowerStub = (state, power, _) => {
        powerOffered += power
        DynamicPowerResult(state, Set.empty, Power.ofFv(15f))
      }
    )
    val consumer2 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(5f),
      usePowerStub = (state, power, _) => {
        powerOffered += power
        DynamicPowerResult(state, Set.empty, Power.ofFv(20f))
      }
    )
    val consumer3 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, power, _) => {
        powerOffered += power
        DynamicPowerResult(state, Set.empty, Power.ofFv(10f))
      }
    )

    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq(consumer1, consumer2, consumer3)
    )

    val processor = DynamicPowerProcessor(
      orderer,
      Set(consumer1, consumer2, consumer3),
      processorConfig
    )

    val state = State()
    val powerDiscarded = 30f
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = powerDiscarded,
      linesPower = List.empty
    )

    val (_, _) = processor.process(state, event, now)

    // Total dynamic power = powerDiscarded + currentlyUsedDynamicPower from all consumers
    // = 30 + 10 + 5 + 0 = 45
    val totalDynamicPower = Power.ofFv(45f)

    assertEquals(powerOffered.size, 3, "All three consumers should be called")
    assertEquals(
      powerOffered(0),
      totalDynamicPower,
      "First consumer gets total dynamic power"
    )
    assertEquals(
      powerOffered(1),
      totalDynamicPower - Power.ofFv(15f),
      "Second consumer gets remaining after first used 15"
    )
    assertEquals(
      powerOffered(2),
      totalDynamicPower - Power.ofFv(15f) - Power.ofFv(20f),
      "Third consumer gets remaining after first two"
    )
  }

  test(
    "DynamicPowerProcessor stops offering power when remaining power is zero or negative"
  ) {
    val callCount = ListBuffer.empty[Int]

    val consumer1 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        callCount += 1
        DynamicPowerResult(state, Set.empty, Power.ofFv(25f))
      }
    )
    val consumer2 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        callCount += 2
        DynamicPowerResult(state, Set.empty, Power.ofFv(10f))
      }
    )
    val consumer3 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        callCount += 3
        DynamicPowerResult(state, Set.empty, Power.ofFv(5f))
      }
    )

    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq(consumer1, consumer2, consumer3)
    )

    val processor = DynamicPowerProcessor(
      orderer,
      Set(consumer1, consumer2, consumer3),
      processorConfig
    )

    val state = State()
    val powerDiscarded = 25f
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = powerDiscarded,
      linesPower = List.empty
    )

    val (_, _) = processor.process(state, event, now)

    assertEquals(
      callCount.toList,
      List(1),
      "Only first consumer should be called as it consumes all available power"
    )
  }

  test("DynamicPowerProcessor aggregates state changes from consumers") {
    import com.softwaremill.quicklens.*

    val consumer1 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        val newState =
          state
            .modify(_.powerManagement.production.powerAvailable)
            .setTo(Some(100f))
        DynamicPowerResult(newState, Set.empty, Power.ofFv(10f))
      }
    )
    val consumer2 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        val newState =
          state
            .modify(_.powerManagement.production.powerProduced)
            .setTo(Some(50f))
        DynamicPowerResult(newState, Set.empty, Power.ofFv(5f))
      }
    )

    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq(consumer1, consumer2)
    )

    val processor = DynamicPowerProcessor(
      orderer,
      Set(consumer1, consumer2),
      processorConfig
    )

    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = 30f,
      linesPower = List.empty
    )

    val (finalState, _) = processor.process(state, event, now)

    assertEquals(
      finalState.powerManagement.production.powerAvailable,
      Some(100f),
      "State changes from consumer1 should be preserved"
    )
    assertEquals(
      finalState.powerManagement.production.powerProduced,
      Some(50f),
      "State changes from consumer2 should be applied on top of consumer1's changes"
    )
  }

  test(
    "DynamicPowerProcessor aggregates actions from consumers, plus the total"
  ) {
    val action1 = Action.SetUIItemValue("Item1", "value1")
    val action2 = Action.SetUIItemValue("Item2", "value2")
    val action3 = Action.SetUIItemValue("Item3", "value3")
    val total = Action.SetUIItemValue(
      processorConfig.dynamicFVPowerUsedItem,
      "15.0"
    )

    val consumer1 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        DynamicPowerResult(state, Set(action1), Power.ofFv(10f))
      }
    )
    val consumer2 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(0f),
      usePowerStub = (state, _, _) => {
        DynamicPowerResult(state, Set(action2, action3), Power.ofFv(5f))
      }
    )

    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq(consumer1, consumer2)
    )

    val processor = DynamicPowerProcessor(
      orderer,
      Set(consumer1, consumer2),
      processorConfig
    )

    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = 30f,
      linesPower = List.empty
    )

    val (_, actions) = processor.process(state, event, now)

    assertEquals(
      actions,
      Set[Action](action1, action2, action3, total),
      "All actions from consumers should be aggregated"
    )
  }

  test(
    "DynamicPowerProcessor resets UI item for dynamic FV power used on StartupEvent"
  ) {
    val consumer = DynamicPowerConsumerStub()

    val orderer = DynamicConsumerOrdererStub()

    val processor =
      DynamicPowerProcessor(orderer, Set(consumer), processorConfig)

    val state = State()
    val event = Event.System.StartupEvent

    val (finalState, actions) = processor.process(state, event, now)

    assertEquals(finalState, state, "State should not change")
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(processorConfig.dynamicFVPowerUsedItem, "0")
      ),
      "UI item reset action should be emitted"
    )
  }

  test(
    "DynamicPowerProcessor calculates total dynamic power including currently used power"
  ) {
    val powerOffered = ListBuffer.empty[Power]

    val consumer1 = DynamicPowerConsumerStub(
      currentlyUsedDynamicPowerStub = (_, _) => Power.ofFv(20f),
      usePowerStub = (state, power, _) => {
        powerOffered += power
        DynamicPowerResult(state, Set.empty, Power.ofFv(0f))
      }
    )

    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq(consumer1)
    )

    val processor =
      DynamicPowerProcessor(orderer, Set(consumer1), processorConfig)

    val state = State()
    val powerDiscarded = 30f
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = powerDiscarded,
      linesPower = List.empty
    )

    val (_, _) = processor.process(state, event, now)

    assertEquals(
      powerOffered(0),
      Power.ofFv(50f),
      "Total dynamic power should be powerDiscarded (30) + currentlyUsedDynamicPower (20)"
    )
  }

  test(
    "DynamicPowerProcessor with no consumers returns state unchanged and only total action"
  ) {
    val orderer = DynamicConsumerOrdererStub(
      orderConsumersStub = (_, _) => Seq.empty
    )

    val processor = DynamicPowerProcessor(orderer, Set.empty, processorConfig)

    val state = State()
    val event = Event.Power.PowerProductionReported(
      powerAvailable = 100f,
      powerProduced = 50f,
      powerDiscarded = 30f,
      linesPower = List.empty
    )

    val (finalState, actions) = processor.process(state, event, now)

    assertEquals(
      finalState,
      state,
      "State should remain unchanged when there are no consumers"
    )
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(
          processorConfig.dynamicFVPowerUsedItem,
          "0.0"
        )
      ),
      "No actions should be emitted when there are no consumers"
    )
  }

  test(
    "DynamicPowerProcessor calls addMissingConsumersToState on StartupEvent and returns the result"
  ) {
    import com.softwaremill.quicklens.*

    val consumer = DynamicPowerConsumerStub()

    val expectedState = State()
      .modify(_.powerManagement.production.powerAvailable)
      .setTo(Some(999f))

    var addMissingConsumersCalled = false
    val orderer = DynamicConsumerOrdererStub(
      addMissingConsumersToStateStub = (_, consumers) => {
        addMissingConsumersCalled = true
        assertEquals(
          consumers,
          Set(consumer),
          "addMissingConsumersToState should receive the correct consumers"
        )
        expectedState
      }
    )

    val processor =
      DynamicPowerProcessor(orderer, Set(consumer), processorConfig)

    val state = State()
    val event = Event.System.StartupEvent

    val (finalState, actions) = processor.process(state, event, now)

    assert(
      addMissingConsumersCalled,
      "addMissingConsumersToState should be called on StartupEvent"
    )
    assertEquals(
      finalState,
      expectedState,
      "State should be the one returned by addMissingConsumersToState"
    )
    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(processorConfig.dynamicFVPowerUsedItem, "0")
      ),
      "UI item reset action should be emitted"
    )
  }

  test(
    "StartupEvent with empty consumers list should generate only the reset action"
  ) {
    import com.softwaremill.quicklens.*

    val orderer = DynamicConsumerOrdererStub(
      addMissingConsumersToStateStub = (state, _) =>
        state.modify(_.powerManagement.dynamic.consumersOrder).setTo(Seq.empty)
    )

    val processor = DynamicPowerProcessor(orderer, Set.empty, processorConfig)

    val state = State()
    val event = Event.System.StartupEvent

    val (_, actions) = processor.process(state, event, now)

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(processorConfig.dynamicFVPowerUsedItem, "0")
      ),
      "Only reset action should be generated for empty consumer list"
    )
  }

  test(
    "StartupEvent with one consumer should generate priority action with value 1"
  ) {
    import com.softwaremill.quicklens.*

    val consumerCode = "TestConsumer1"
    val consumer = DynamicPowerConsumerStub(code = consumerCode)

    val orderer = DynamicConsumerOrdererStub(
      addMissingConsumersToStateStub = (state, _) =>
        state
          .modify(_.powerManagement.dynamic.consumersOrder)
          .setTo(Seq(consumerCode))
    )

    val processor =
      DynamicPowerProcessor(orderer, Set(consumer), processorConfig)

    val state = State()
    val event = Event.System.StartupEvent

    val (_, actions) = processor.process(state, event, now)

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(processorConfig.dynamicFVPowerUsedItem, "0"),
        Action.SetUIItemValue(consumerCode, "1")
      ),
      "Should generate reset action and priority action with value 1"
    )
  }

  test(
    "StartupEvent with multiple consumers should generate priority actions with values equal to position + 1"
  ) {
    import com.softwaremill.quicklens.*

    val consumerCode1 = "TestConsumer1"
    val consumerCode2 = "TestConsumer2"
    val consumerCode3 = "TestConsumer3"

    val consumer1 = DynamicPowerConsumerStub(code = consumerCode1)
    val consumer2 = DynamicPowerConsumerStub(code = consumerCode2)
    val consumer3 = DynamicPowerConsumerStub(code = consumerCode3)

    val orderer = DynamicConsumerOrdererStub(
      addMissingConsumersToStateStub = (state, _) =>
        state
          .modify(_.powerManagement.dynamic.consumersOrder)
          .setTo(Seq(consumerCode1, consumerCode2, consumerCode3))
    )

    val processor = DynamicPowerProcessor(
      orderer,
      Set(consumer1, consumer2, consumer3),
      processorConfig
    )

    val state = State()
    val event = Event.System.StartupEvent

    val (_, actions) = processor.process(state, event, now)

    assertEquals(
      actions,
      Set[Action](
        Action.SetUIItemValue(processorConfig.dynamicFVPowerUsedItem, "0"),
        Action.SetUIItemValue(consumerCode1, "1"),
        Action.SetUIItemValue(consumerCode2, "2"),
        Action.SetUIItemValue(consumerCode3, "3")
      ),
      "Should generate reset action and priority actions with values 1, 2, and 3"
    )
  }
}
