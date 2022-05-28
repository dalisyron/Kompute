package stochastic.lp

import com.google.ortools.Loader
import com.google.ortools.glop.GlopParameters
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPSolver
import java.lang.IllegalArgumentException
import java.util.Collections.max
import kotlin.math.pow

fun MPSolver.makeEqualityConstraint(rhs: Double, name: String): MPConstraint {
    return makeConstraint(rhs, rhs, name)
}

data class LPSolution(
    val objectiveValue: Double,
    val variableValues: List<Double>,
    val isAbnormal: Boolean
)

object LPSolver {

    init {
        Loader.loadNativeLibraries()
    }

    fun solve(lp: StandardLinearProgram, retryCounter: Int = 0): LPSolution {
        val rows = lp.rows.requireNoNulls()
        check(rows.map { it.coefficients.size }
            .toSet().size == 1) // Check there is an equal number of variables for each row

        check(retryCounter in 0..2)

        val objectiveCount = rows.filter { it.type == EquationRow.Type.Objective }.size
        check(objectiveCount == 1) {
            "Need exactly one objective function. Found $objectiveCount"
        }

        val constraintCount = rows.size - objectiveCount
        check(constraintCount >= 1) {
            "Need at least one constraint"
        }

        val variableCount = rows[0].coefficients.size

        val solver = MPSolver.createSolver("GLOP")

        if (retryCounter == 1) {
            solver.setSolverSpecificParametersAsString(
                GlopParameters.newBuilder().setUsePreprocessing(false).build().toString()
            )
        } else if (retryCounter == 2) {
            println("Changing tolerance to 1e-4")
            solver.setSolverSpecificParametersAsString(
                GlopParameters.newBuilder().setSolutionFeasibilityTolerance(1e-4).build().toString()
            )
        }

        checkNotNull(solver) {
            "Could not create solver SCIP"
        }

        val infinity = Double.POSITIVE_INFINITY

        val variables = (1..variableCount).map { index ->
            solver.makeNumVar(0.0, infinity, "x$index")
        }

        var constraintIndex = 1
        rows.forEach {
            when (it.type) {
                EquationRow.Type.Objective -> {
                    val objective = solver.objective()
                    objective.setOffset(-it.rhs)
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
                    val constraint = solver.makeConstraint(Double.NEGATIVE_INFINITY, it.rhs, "c$constraintIndex")
                    for (i in 0 until variableCount) {
                        constraint.setCoefficient(variables[i], it.coefficients[i])
                    }
                    constraintIndex++
                }
            }
        }

        val resultStatus = solver.solve()

        if (resultStatus != MPSolver.ResultStatus.OPTIMAL) {
            if (retryCounter <= 1) {
                return solve(lp, retryCounter + 1)
            } else {
                return LPSolution(
                    objectiveValue = Double.POSITIVE_INFINITY,
                    variableValues = variables.map { it.solutionValue() },
                    isAbnormal = true
                )
            }
        }

        return LPSolution(
            objectiveValue = solver.objective().value(),
            variableValues = variables.map { it.solutionValue() },
            isAbnormal = false
        )
    }

    class AbnormalSolutionException : RuntimeException()
}