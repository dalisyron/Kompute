import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import kotlin.jvm.JvmStatic
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolver.ResultStatus // [END import]

fun MPSolver.makeEqualityConstraint(rhs: Double, name: String): MPConstraint {
    return makeConstraint(rhs, rhs, name)
}

object SimpleLpProgram {
    @JvmStatic
    fun main(args: Array<String>) {
        Loader.loadNativeLibraries()
        // [START solver]
        // Create the linear solver with the GLOP backend.
        val solver = MPSolver.createSolver("GLOP")
        if (solver == null) {
            println("Could not create solver SCIP")
            return
        }
        // [END solver]

        // [START variables]
        val infinity = Double.POSITIVE_INFINITY
        // Create the variables x and y.
        val x = solver.makeNumVar(0.0, infinity, "x")
        val y = solver.makeNumVar(0.0, infinity, "y")
        println("Number of variables = " + solver.numVariables())
        // [END variables]

        // [START constraints]
        // x + 7 * y <= 17.5.
        val c0 = solver.makeConstraint(-infinity, 17.5, "c0")
        c0.setCoefficient(x, 1.0)
        c0.setCoefficient(y, 7.0)

        // x <= 3.5.
        val c1 = solver.makeConstraint(-infinity, 3.5, "c1")
        c1.setCoefficient(x, 1.0)
        c1.setCoefficient(y, 0.0)
        println("Number of constraints = " + solver.numConstraints())
        // [END constraints]

        // [START objective]
        // Maximize x + 10 * y.
        val objective = solver.objective()
        objective.setCoefficient(x, 1.0)
        objective.setCoefficient(y, 10.0)
        objective.setMaximization()
        // [END objective]

        // [START solve]
        val resultStatus = solver.solve()
        // [END solve]

        // [START print_solution]
        if (resultStatus == ResultStatus.OPTIMAL) {
            println("Solution:")
            println("Objective value = " + objective.value())
            println("x = " + x.solutionValue())
            println("y = " + y.solutionValue())
        } else {
            System.err.println("The problem does not have an optimal solution!")
        }
        // [END print_solution]

        // [START advanced]
        println("\nAdvanced usage:")
        println("Problem solved in " + solver.wallTime() + " milliseconds")
        println("Problem solved in " + solver.iterations() + " iterations")
        // [END advanced]
    }
}