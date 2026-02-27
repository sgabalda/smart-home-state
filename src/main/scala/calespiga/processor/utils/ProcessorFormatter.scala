package calespiga.processor.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ProcessorFormatter:
  val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  def format(instant: Instant, zone: ZoneId): String =
    instant.atZone(zone).format(formatter)
