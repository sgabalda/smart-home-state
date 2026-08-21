package calespiga.processor.power.dynamic

final case class Power(fv: Float, grid: Float) {
  def +(other: Power): Power = Power(this.fv + other.fv, this.grid + other.grid)
  def -(other: Power): Power = Power(this.fv - other.fv, this.grid - other.grid)
  def <=(other: Power): Boolean = this.fv + this.grid <= other.fv + other.grid
}

object Power {

  val zero: Power = Power(0f, 0f)

  def ofFv(fv: Float): Power = Power(fv, 0f)

  def ofGrid(grid: Float): Power = Power(0f, grid)

}
