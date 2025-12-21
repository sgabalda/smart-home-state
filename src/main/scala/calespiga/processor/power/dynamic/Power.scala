package calespiga.processor.power.dynamic

// if there are more types (e.g. grid), they can be added here
final case class Power(unusedFV: Float) {
  def +(other: Power): Power = Power(this.unusedFV + other.unusedFV)
  def -(other: Power): Power = Power(this.unusedFV - other.unusedFV)
  def <=(other: Power): Boolean = this.unusedFV <= other.unusedFV
}

object Power {

  val zero: Power = Power(0f)

  def ofUnusedFV(unusedFV: Float): Power = Power(unusedFV)

}
