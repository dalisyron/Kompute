package stochastic.lp

import ilog.concert.IloNumExpr
import ilog.concert.IloNumVar
import ilog.cplex.IloCplex
import java.io.OutputStream

object CPLEXSolver {

    fun solve(lp: StandardLinearProgram): LPSolution {
        val rows = lp.rows.requireNoNulls()
        check(rows.map { it.coefficients.size }
            .toSet().size == 1) {
            rows.mapIndexed { index, equationRow -> equationRow.coefficients.size to index}.groupBy { it.first }.mapValues { it -> it.value.map { it.second } }
        } // Check there is an equal number of variables for each row

        val objectiveCount = rows.filter { it.type == EquationRow.Type.Objective }.size
        check(objectiveCount == 1) {
            "Need exactly one objective function. Found $objectiveCount"
        }

        val constraintCount = rows.size - objectiveCount
        check(constraintCount >= 1) {
            "Need at least one constraint"
        }

        val variableCount = rows[0].coefficients.size

        val cplex = IloCplex()
        // cplex.setOut(OutputStream.nullOutputStream())

        val variables: List<IloNumVar> = (1..variableCount).map { index ->
            cplex.numVar(0.0, Double.MAX_VALUE, "x$index")
        }

        var objectiveRow: EquationRow? = null

        rows.forEach {
            when (it.type) {
                EquationRow.Type.Equality, EquationRow.Type.LessThan -> {
                    cplex.addConstraint(it.type, variables, it.coefficients, it.rhs)
                }
                EquationRow.Type.Objective -> {
                    cplex.setObjective(variables, it.coefficients)
                    objectiveRow = it
                }
            }
        }

        requireNotNull(objectiveRow)

        val isSolved = cplex.solve()

        if (!isSolved) {
            return LPSolution(
                -1.0,
                listOf(),
                true
            )
        }

        requireNotNull(objectiveRow)

        val objectiveValue: Double = cplex.objValue - objectiveRow!!.rhs

        val variableValues: List<Double> = variables.map { cplex.getValue(it) }

        return LPSolution(
            objectiveValue,
            variableValues,
            false
        )
    }

    private fun IloCplex.addConstraint(type: EquationRow.Type, variables: List<IloNumVar>, coefficients: List<Double>, rhs: Double) {
        require(variables.size == coefficients.size)
        require(variables.isNotEmpty())

        val products: Array<IloNumExpr> = variables.mapIndexed { index, iloNumVar ->
            prod(coefficients[index], iloNumVar)
        }.toTypedArray()

        val expression = sum(products)

        when (type) {
            EquationRow.Type.Equality -> {
                addEq(expression, rhs)
            }
            EquationRow.Type.LessThan -> {
                addLe(expression, rhs)
            }
            else -> {
                throw java.lang.IllegalArgumentException()
            }
        }
    }

    private fun IloCplex.setObjective(variables: List<IloNumVar>, coefficients: List<Double>) {
        require(variables.size == coefficients.size)
        require(variables.isNotEmpty())

        val objective = linearNumExpr()

        variables.forEachIndexed { index, iloNumVar ->
            objective.addTerm(coefficients[index], iloNumVar)
        }
        addMinimize(objective)
    }
}