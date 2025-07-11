package calespiga.mqtt

import calespiga.model.Event
import calespiga.mqtt.MqttToEventInputProcessor.TopicMessagesConverter

import scala.util.Try

trait InputTopicsManager {

  def inputTopics: Set[String]

  def inputTopicsConversions: TopicMessagesConverter
}

object InputTopicsManager {

  def apply: InputTopicsManager = new InputTopicsManager {

    override def inputTopics: Set[String] = inputTopicsConversions.keySet

    override def inputTopicsConversions: TopicMessagesConverter =
      Event.eventsMqttMessagesConverter.map {
        case (topic, constructorOfString) => {

          val conversion: Vector[Byte] => Either[Throwable, Event.EventData] =
            v =>
              Try(constructorOfString(new String(v.toArray, "UTF-8"))).toEither
          (topic, conversion)
        }
      }.toMap
  }

}
