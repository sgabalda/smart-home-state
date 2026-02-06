package calespiga.processor.power.dynamic

import munit.FunSuite
import calespiga.model.State
import com.softwaremill.quicklens.*

class DynamicConsumerOrdererSuite extends FunSuite {

  // Helper to create consumers with specific unique codes
  private def createConsumerWithCode(code: String): DynamicPowerConsumer = {
    new DynamicPowerConsumer {
      override val uniqueCode: String = code
      override def currentlyUsedDynamicPower(
          state: State,
          now: java.time.Instant
      ): Power = Power.zero
      override def usePower(
          state: State,
          powerToUse: Power,
          now: java.time.Instant
      ): DynamicPowerConsumer.DynamicPowerResult =
        DynamicPowerConsumer.DynamicPowerResult(state, Set.empty, Power.zero)
    }
  }

  test(
    "addMissingConsumersToState does not modify state when no consumers are missing"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")
    val consumer3 = createConsumerWithCode("consumer3")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2", "consumer3"))

    val consumers = Set(consumer1, consumer2, consumer3)

    val result = orderer.addMissingConsumersToState(state, consumers)

    assertEquals(
      result.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", "consumer2", "consumer3"),
      "Consumer order should remain unchanged when all consumers are already present"
    )
  }

  test(
    "addMissingConsumersToState adds missing consumers at the end"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")
    val consumer3 = createConsumerWithCode("consumer3")
    val consumer4 = createConsumerWithCode("consumer4")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2"))

    val consumers = Set(consumer1, consumer2, consumer3, consumer4)

    val result = orderer.addMissingConsumersToState(state, consumers)

    assert(
      result.powerManagement.dynamic.consumersOrder
        .startsWith(Seq("consumer1", "consumer2")),
      "Existing consumers should remain at the beginning in their original order"
    )
    assert(
      result.powerManagement.dynamic.consumersOrder.contains("consumer3"),
      "Missing consumer3 should be added"
    )
    assert(
      result.powerManagement.dynamic.consumersOrder.contains("consumer4"),
      "Missing consumer4 should be added"
    )
    assertEquals(
      result.powerManagement.dynamic.consumersOrder.size,
      4,
      "All four consumers should be in the order"
    )
  }

  test(
    "addMissingConsumersToState adds all consumers when state order is empty"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")
    val consumer3 = createConsumerWithCode("consumer3")

    val state = State()

    val consumers = Set(consumer1, consumer2, consumer3)

    val result = orderer.addMissingConsumersToState(state, consumers)

    assertEquals(
      result.powerManagement.dynamic.consumersOrder.size,
      3,
      "All three consumers should be added to empty order"
    )
    assert(
      result.powerManagement.dynamic.consumersOrder.contains("consumer1"),
      "consumer1 should be added"
    )
    assert(
      result.powerManagement.dynamic.consumersOrder.contains("consumer2"),
      "consumer2 should be added"
    )
    assert(
      result.powerManagement.dynamic.consumersOrder.contains("consumer3"),
      "consumer3 should be added"
    )
  }

  test(
    "addMissingConsumersToState removes all consumers when consumers set is empty"
  ) {
    val orderer = DynamicConsumerOrderer()

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2"))

    val result = orderer.addMissingConsumersToState(state, Set.empty)

    assertEquals(
      result.powerManagement.dynamic.consumersOrder,
      Seq.empty,
      "All consumers should be removed when consumers set is empty"
    )
  }

  test(
    "addMissingConsumersToState removes consumers from state that are not in the provided consumers set"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer3 = createConsumerWithCode("consumer3")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2", "consumer3", "consumer4"))

    val consumers = Set(consumer1, consumer3)

    val result = orderer.addMissingConsumersToState(state, consumers)

    assertEquals(
      result.powerManagement.dynamic.consumersOrder,
      Seq("consumer1", "consumer3"),
      "Only consumers present in the provided set should remain in the order"
    )
    assert(
      !result.powerManagement.dynamic.consumersOrder.contains("consumer2"),
      "consumer2 should be removed as it's not in the provided consumers set"
    )
    assert(
      !result.powerManagement.dynamic.consumersOrder.contains("consumer4"),
      "consumer4 should be removed as it's not in the provided consumers set"
    )
  }

  test(
    "orderConsumers returns consumers in the order specified in state"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")
    val consumer3 = createConsumerWithCode("consumer3")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer2", "consumer1", "consumer3"))

    val consumers = Set(consumer1, consumer2, consumer3)

    val result = orderer.orderConsumers(state, consumers)

    assertEquals(
      result,
      Seq(consumer2, consumer1, consumer3),
      "Consumers should be ordered according to state's consumersOrder"
    )
  }

  test(
    "orderConsumers excludes consumers not present in state order"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")
    val consumer3 = createConsumerWithCode("consumer3")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer3"))

    val consumers = Set(consumer1, consumer2, consumer3)

    val result = orderer.orderConsumers(state, consumers)

    assertEquals(
      result,
      Seq(consumer1, consumer3),
      "Only consumers in state's consumersOrder should be returned"
    )
  }

  test(
    "orderConsumers ignores codes in state that don't match any consumer"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "nonexistent", "consumer2", "alsoNonexistent"))

    val consumers = Set(consumer1, consumer2)

    val result = orderer.orderConsumers(state, consumers)

    assertEquals(
      result,
      Seq(consumer1, consumer2),
      "Non-matching codes in state should be ignored"
    )
  }

  test(
    "orderConsumers returns empty sequence when state order is empty"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer2 = createConsumerWithCode("consumer2")

    val state = State()

    val consumers = Set(consumer1, consumer2)

    val result = orderer.orderConsumers(state, consumers)

    assertEquals(
      result,
      Seq.empty,
      "Should return empty sequence when state consumerOrder is empty"
    )
  }

  test(
    "orderConsumers returns empty sequence when consumers set is empty"
  ) {
    val orderer = DynamicConsumerOrderer()

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2"))

    val result = orderer.orderConsumers(state, Set.empty)

    assertEquals(
      result,
      Seq.empty,
      "Should return empty sequence when consumers set is empty"
    )
  }

  test(
    "orderConsumers handles partial overlap between state and consumers"
  ) {
    val orderer = DynamicConsumerOrderer()

    val consumer1 = createConsumerWithCode("consumer1")
    val consumer3 = createConsumerWithCode("consumer3")
    val consumer4 = createConsumerWithCode("consumer4")

    val state = State()
      .modify(_.powerManagement.dynamic.consumersOrder)
      .setTo(Seq("consumer1", "consumer2", "consumer3", "consumer5"))

    val consumers = Set(consumer1, consumer3, consumer4)

    val result = orderer.orderConsumers(state, consumers)

    assertEquals(
      result,
      Seq(consumer1, consumer3),
      "Should only return consumers that are both in state order and consumers set"
    )
  }
}
