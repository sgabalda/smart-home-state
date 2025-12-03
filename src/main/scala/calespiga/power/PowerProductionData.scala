package calespiga.power

private[power] final case class PowerProductionData(
    powerAvailable: Float, // available = produced + discarded
    powerProduced: Float,
    powerDiscarded: Float,
    linesPower: List[Float]
)
