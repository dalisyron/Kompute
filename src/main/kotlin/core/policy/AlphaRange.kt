package core.policy

sealed class AlphaRange {

    abstract fun toList(): List<Double>

    data class Constant(val value: Double) : AlphaRange() {
        init {
            require(value > 0 && value <= 1.0)
        }

        override fun toList(): List<Double> = listOf(value)
    }

    data class Variable(val start: Double, val end: Double, val count: Int) : AlphaRange() {
        init {
            require(start > 0 && start <= 1.0)
            require(end > 0 && end <= 1.0)
            if (count == 1) {
                require(start == end)
            }
        }

        override fun toList(): List<Double> {
            if (count == 1) {
                return listOf(start)
            }
            return (0 until count).map { i ->
                start + i * ((end - start) / (count - 1))
            }
        }
    }
}