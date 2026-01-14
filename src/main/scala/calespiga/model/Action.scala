package calespiga.model

import scala.concurrent.duration.FiniteDuration

sealed trait Action

object Action {

  sealed trait Direct extends Action
  case class SetUIItemValue(
      item: String,
      value: String
  ) extends Direct
  case class SendMqttStringMessage(
      topic: String,
      message: String
  ) extends Direct

  /** Send a notification to the user. Processors may repeat the notification on
    * each execution blindly, and the repeatInterval indicates how often it will
    * be resent to the user. If None, the notification is repeated using the
    * default interval.
    *
    * @param id
    * @param message
    * @param repeatInterval
    */
  case class SendNotification(
      id: String,
      message: String,
      repeatInterval: Option[FiniteDuration]
  ) extends Direct

  sealed trait Scheduled extends Action
  case class Delayed(id: String, action: Direct, delay: FiniteDuration)
      extends Scheduled
  case class Periodic(id: String, action: Direct, period: FiniteDuration)
      extends Scheduled
  case class Cancel(id: String) extends Scheduled

}
