package calespiga.processor

import munit.CatsEffectSuite
import cats.effect.IO
import cats.effect.Ref
import calespiga.model.{State, Event}
import java.time.Instant
import com.softwaremill.quicklens.*
import calespiga.config.FeatureFlagsConfig
import calespiga.model.Action

class FeatureFlagsProcessorSuite extends CatsEffectSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val startupEvent = Event.System.StartupEvent

  val dummyConfig = FeatureFlagsConfig(
    heaterMqttTopic = Set("heater/topic1", "heater/topic2"),
    setHeaterManagementItem = "SetHeaterManagementSHS"
  )

  test(
    "StartupEvent with feature flags false adds blacklist items and sets items"
  ) {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set.empty)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State()
        .modify(_.featureFlags.heaterManagementEnabled)
        .setTo(false)
      (_, actions) <- processor.process(state, startupEvent, now)
      blacklist <- blacklistRef.get
    } yield {
      assert(blacklist.contains("heater/topic1"))
      assert(blacklist.contains("heater/topic2"))
      assertEquals(
        actions,
        Set[Action](
          Action.SetUIItemValue(
            dummyConfig.setHeaterManagementItem,
            "false"
          )
        )
      )
    }
  }

  test("StartupEvent with feature flags true does not add blacklist items") {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set.empty)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State()
        .modify(_.featureFlags.heaterManagementEnabled)
        .setTo(true)
      (_, actions) <- processor.process(state, startupEvent, now)
      blacklist <- blacklistRef.get
    } yield {
      assertEquals(blacklist, Set.empty)
      assertEquals(
        actions,
        Set[Action](
          Action.SetUIItemValue(
            dummyConfig.setHeaterManagementItem,
            "true"
          )
        )
      )
    }
  }

  test(
    "SetHeaterManagement(false) adds heater topics to blacklist and disables flag"
  ) {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set.empty)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State().modify(_.featureFlags.heaterManagementEnabled).setTo(true)
      (newState, _) <- processor.process(
        state,
        Event.FeatureFlagEvents.SetHeaterManagement(false),
        now
      )
      blacklist <- blacklistRef.get
    } yield {
      assert(blacklist.contains("heater/topic1"))
      assert(blacklist.contains("heater/topic2"))
      assertEquals(newState.featureFlags.heaterManagementEnabled, false)
    }
  }

  test(
    "SetHeaterManagement(true) removes heater topics from blacklist and enables flag"
  ) {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](
        Set("heater/topic1", "heater/topic2")
      )
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State()
        .modify(_.featureFlags.heaterManagementEnabled)
        .setTo(false)
      (newState, _) <- processor.process(
        state,
        Event.FeatureFlagEvents.SetHeaterManagement(true),
        now
      )
      blacklist <- blacklistRef.get
    } yield {
      assert(!blacklist.contains("heater/topic1"))
      assert(!blacklist.contains("heater/topic2"))
      assertEquals(newState.featureFlags.heaterManagementEnabled, true)
    }
  }
}
