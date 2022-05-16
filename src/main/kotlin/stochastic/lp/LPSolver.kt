package stochastic.lp

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver

fun MPSolver.makeEqualityConstraint(rhs: Double, name: String): MPConstraint {
    return makeConstraint(rhs, rhs, name)
}

data class LPSolution(
    val objectiveValue: Double,
    val variableValues: List<Double>
)

object LPSolver {

    init {
        Loader.loadNativeLibraries()
    }

    fun solve(lp: StandardLinearProgram): LPSolution {
        check(lp.rows.map { it.coefficients.size }
            .toSet().size == 1) // Check there is an equal number of variables for each row

        val objectiveCount = lp.rows.filter { it.type == EquationRow.Type.Objective }.size
        check(objectiveCount == 1) {
            "Need exactly one objective function. Found $objectiveCount"
        }

        val constraintCount = lp.rows.size - objectiveCount
        check(constraintCount >= 1) {
            "Need at least one constraint"
        }

        val variableCount = lp.rows[0].coefficients.size

        val solver = MPSolver.createSolver("GLOP")

        checkNotNull(solver) {
            "Could not create solver SCIP"
        }

        val infinity = Double.POSITIVE_INFINITY

        val variables = (1..variableCount).map {
            solver.makeNumVar(0.0, infinity, "x$it")
        }

        var constraintIndex = 1
        lp.rows.forEach {
            when (it.type) {
                EquationRow.Type.Objective -> {
                    val objective = solver.objective()
                    objective.setOffset(it.rhs)
                    objective.setMinimization()

                    for (i in 0 until variableCount) {
                        objective.setCoefficient(variables[i], it.coefficients[i])
                    }
                }
                EquationRow.Type.Equality -> {
                    val constraint = solver.makeEqualityConstraint(it.rhs, "c$constraintIndex")
                    for (i in 0 until variableCount) {
                        constraint.setCoefficient(variables[i], it.coefficients[i])
                    }
                    constraintIndex++
                }
                EquationRow.Type.LessThan -> {
                    val constraint = solver.makeConstraint(0.0, infinity, "c$constraintIndex")
                    for (i in 0 until variableCount) {
                        constraint.setCoefficient(variables[i], it.coefficients[i])
                    }
                    constraintIndex++
                }
            }
        }

        val resultStatus = solver.solve()

        check(resultStatus == MPSolver.ResultStatus.OPTIMAL) {
            "The problem does not have an optimal solution."
        }

        return LPSolution(
            objectiveValue = solver.objective().value(),
            variableValues = variables.map { it.solutionValue() }
        )
    }
}