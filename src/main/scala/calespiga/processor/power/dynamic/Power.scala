package calespiga.processor.power.dynamic

// if there are more types (e.g. grid), they can be added here
final case class Power(fv: Float) {
  def +(other: Power): Power = Power(this.fv + other.fv)
  def -(other: Power): Power = Power(this.fv - other.fv)
  def <=(other: Power): Boolean = this.fv <= other.fv
}

object Power {

  val zero: Power = Power(0f)

  def ofFv(fv: Float): Power = Power(fv)

}
