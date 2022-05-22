package stochastic.lp

data class EquationRow(
    val coefficients: List<Double>,
    val rhs: Double,
    val type: Type = Type.Equality
) {

    enum class Type {
        Equality, LessThan, Objective
    }
}

class StandardLinearProgram(
    val rows: List<EquationRow>,
    val zeroVariables: Set<Int>
)