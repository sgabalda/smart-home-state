package calespiga.processor.power.dynamic

/** Aggregated available power coming from both solar production and the grid.
  *
  * A consumer may first drain from the FV source and then, if needed, use grid
  * power. The methods in this type make that preference explicit.
  */
final case class Power(fv: Float, grid: Float) {
  def +(other: Power): Power = Power(this.fv + other.fv, this.grid + other.grid)
  def -(other: Power): Power = Power(this.fv - other.fv, this.grid - other.grid)
  def <=(other: Power): Boolean = this.fv + this.grid <= other.fv + other.grid
  def withOnlyFv: Power = Power(this.fv, 0)
  def withOnlyGrid: Power = Power(0, this.grid)
  def total: Float = this.fv + this.grid

  /** Consumes the requested amount from the FV source only. */
  def consumeFromFv(watts: Float): Option[Power] =
    if (this.fv >= watts) Some(Power(this.fv - watts, this.grid))
    else None

  /** Consumes the requested amount by using FV first and only then grid power.
    */
  def consumeFvThenGrid(watts: Float): Option[Power] =
    if (this.fv >= watts) Some(Power(this.fv - watts, this.grid))
    else if (this.total >= watts) Some(Power(0.0f, this.grid + this.fv - watts))
    else None
}

object Power {

  val zero: Power = Power(0f, 0f)

  def ofFv(fv: Float): Power = Power(fv, 0f)

  def ofGrid(grid: Float): Power = Power(0f, grid)

}
