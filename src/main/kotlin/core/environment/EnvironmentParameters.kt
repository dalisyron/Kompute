package core.environment

data class EnvironmentParameters(
    val nCloud: List<Int>,
    val tRx: Double,
) {

    companion object {
        fun singleQueue(nCloud: Int, tRx: Double): EnvironmentParameters {
            return EnvironmentParameters(listOf(nCloud), tRx)
        }
    }
}