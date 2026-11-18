package calespiga.mqtt.annotations

import scala.annotation.StaticAnnotation

case class InputEventMqtt(topic: String) extends StaticAnnotation
