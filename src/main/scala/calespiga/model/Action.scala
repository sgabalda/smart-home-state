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

  /**
    * Action to send an event to the system.
    *
    * @param event
    */
  case class SendFeedbackEvent(
      event: Event.FeedbackEventData
  ) extends Direct
  

  sealed trait Scheduled extends Action
  /**
    * Action to execute another action after a specified delay. 
    * The action will be executed once after the delay has passed.
    *
    * @param id
    * @param action
    * @param delay
    */
  case class Delayed(id: String, action: Direct, delay: FiniteDuration)
      extends Scheduled
  /**
    * Action to execute another action periodically with a specified interval.
    *
    * @param id
    * @param action
    * @param period
    * @param differentInitialDelay if defined, the initial delay before the first execution will be 
    *         different from the period. If None, the first execution will occur after the same duration as the period. 
    */
  case class Periodic(id: String, action: Direct, period: FiniteDuration, differentInitialDelay: Option[FiniteDuration] = None)
      extends Scheduled
  
  /**
    * Action to cancel a scheduled action with the specified ID. This can be used to 
    * stop a periodic action or prevent a delayed action from executing if it has not yet started. 
    * The processor must ensure that the ID corresponds to an existing scheduled action that should be canceled.
    *
    * @param id
    */
  case class Cancel(id: String) extends Scheduled

}
