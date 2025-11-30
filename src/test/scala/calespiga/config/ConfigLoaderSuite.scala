package calespiga.config

import cats.effect.IO
import munit.CatsEffectSuite

class ConfigLoaderSuite extends CatsEffectSuite {

  test("loadResource: successfully loads application.conf") {
    ConfigLoader.loadResource.use { config =>
      IO {
        // Verify that the config was loaded successfully
        assert(config != null, "Config should not be null")
      }
    }
  }
}
