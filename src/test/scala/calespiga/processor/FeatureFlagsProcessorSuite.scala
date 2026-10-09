package calespiga.processor

import calespiga.model.Action
import calespiga.model.Event
import calespiga.model.State
import cats.effect.IO
import cats.effect.Ref
import com.softwaremill.quicklens.*
import java.time.Instant
import munit.CatsEffectSuite

class FeatureFlagsProcessorSuite extends CatsEffectSuite {
  val now = Instant.parse("2023-08-17T10:00:00Z")
  val startupEvent = Event.System.StartupEvent

  val dummyConfig = ProcessorConfigHelper.featureFlagsConfig

  test("StartupEvent disables Grey water MQTT when the flag is false") {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set("existing/topic"))
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      (_, actions) <- processor.process(State(), startupEvent, now)
      blacklist <- blacklistRef.get
    } yield {
      assertEquals(blacklist, Set("existing/topic", "greyWater/command"))
      assertEquals(
        actions,
        Set[Action](
          Action.SetUIItemValue(
            dummyConfig.setGreyWaterEnabledItem,
            "false"
          )
        )
      )
    }
  }

  test("StartupEvent leaves Grey water MQTT enabled when the flag is true") {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set.empty)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State().modify(_.featureFlags.greyWaterEnabled).setTo(true)
      (_, actions) <- processor.process(state, startupEvent, now)
      blacklist <- blacklistRef.get
    } yield {
      assertEquals(blacklist, Set.empty)
      assertEquals(
        actions,
        Set[Action](
          Action.SetUIItemValue(
            dummyConfig.setGreyWaterEnabledItem,
            "true"
          )
        )
      )
    }
  }

  test("SetGreyWaterEnabled(false) blocks the Grey water command topic") {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](Set.empty)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State().modify(_.featureFlags.greyWaterEnabled).setTo(true)
      (newState, _) <- processor.process(
        state,
        Event.FeatureFlagEvents.SetGreyWaterEnabled(false),
        now
      )
      blacklist <- blacklistRef.get
    } yield {
      assertEquals(blacklist, dummyConfig.greyWaterMqttTopic)
      assertEquals(newState.featureFlags.greyWaterEnabled, false)
    }
  }

  test("SetGreyWaterEnabled(true) unblocks the Grey water command topic") {
    for {
      blacklistRef <- Ref.of[IO, Set[String]](dummyConfig.greyWaterMqttTopic)
      processor = FeatureFlagsProcessor(blacklistRef, dummyConfig)
      state = State()
      (newState, _) <- processor.process(
        state,
        Event.FeatureFlagEvents.SetGreyWaterEnabled(true),
        now
      )
      blacklist <- blacklistRef.get
    } yield {
      assertEquals(blacklist, Set.empty)
      assertEquals(newState.featureFlags.greyWaterEnabled, true)
    }
  }
}
