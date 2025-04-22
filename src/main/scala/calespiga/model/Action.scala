package calespiga.model

import java.time.Instant

sealed trait Action {
  def timestamp: Instant
}
