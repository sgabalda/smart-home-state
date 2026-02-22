package calespiga.model

import scala.concurrent.duration.FiniteDuration

sealed trait Action

object Action {

  sealed trait Direct extends Action

  /** Action to set the value of a UI item. The processor will specify the item name and the value to set. 
    * This can be used to update the user interface based on events or state changes in the system. 
    * The processor must ensure that the item name corresponds to a valid UI item and that the value is in the correct format for that item. 
    *
    * @param item
    * @param value
    */
  case class SetUIItemValue(
      item: String,
      value: String
  ) extends Direct

  /**
    * Action to send an MQTT message with a string payload. 
    * The topic and message are specified in the action parameters. 
    * Processors can use this action to send commands or information to 
    * other systems via MQTT. 
    * The message is sent as-is, so the processor must ensure that the 
    * topic and message are correctly formatted for the intended recipient. 
    *
    * @param topic
    * @param message
    */
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
  case class Periodic(id: String, action: Direct, period: FiniteDuration, differentInitialDelay: Option[FiniteDuration] = None)
      extends Scheduled
  case class Cancel(id: String) extends Scheduled

}
