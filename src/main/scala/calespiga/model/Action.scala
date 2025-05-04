package calespiga.model

sealed trait Action

object Action {

  case class SetOpenHabItemValue(
      item: String,
      value: String
  ) extends Action

}
