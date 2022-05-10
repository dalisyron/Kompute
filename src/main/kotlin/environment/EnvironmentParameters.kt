package environment

data class EnvironmentParameters(
    val nCloud: Int,
    val tRx: Double,
    val eta: Double,
    val pTx: Double,
    val pLoc: Double,
    val nLocal: Int
)