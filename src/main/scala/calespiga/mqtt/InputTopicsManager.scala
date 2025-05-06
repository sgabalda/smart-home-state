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
      Set("diposit1/temperature/batteries")
        .map(t => {
          val topic = t
          val conversion: Vector[Byte] => Either[Throwable, Event.EventData] = v =>
            Try(new String(v.toArray, "UTF-8").toDouble).toEither
              .map(v => Event.Temperature.BatteryTemperatureMeasured(v))
          (topic, conversion)
        })
        .toMap // TODO do this in base of annotations on the events definitions
  }

}
