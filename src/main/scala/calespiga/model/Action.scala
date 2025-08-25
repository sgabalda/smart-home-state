package calespiga.model

import scala.concurrent.duration.FiniteDuration

sealed trait Action

object Action {

  sealed trait Direct extends Action
  case class SetOpenHabItemValue(
      item: String,
      value: String
  ) extends Direct
  case class SendMqttStringMessage(
      topic: String,
      message: String
  ) extends Direct

  sealed trait Scheduled extends Action
  case class Delayed(id: String, action: Direct, delay: FiniteDuration)
      extends Scheduled
  case class Periodic(id: String, action: Direct, period: FiniteDuration)
      extends Scheduled
  case class Cancel(id: String) extends Scheduled

}
