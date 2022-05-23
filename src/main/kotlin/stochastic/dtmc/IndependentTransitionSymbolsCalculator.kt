package stochastic.dtmc

import dtmc.symbol.Symbol
import policy.Action
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import stochastic.dtmc.transition.Edge

// Calculates the following:
// X(s1, s2, k)
// Probability that the next step will be s2 given that we are in s1 and we have made decision k
class IndependentTransitionCalculator(
    private val symbolMapping: Map<Symbol, Double>,
    private val discreteTimeMarkovChain: DiscreteTimeMarkovChain
) {
    fun getIndependentTransitionFraction(source: UserEquipmentState, dest: UserEquipmentState, action: Action): Double {
        val edges = discreteTimeMarkovChain.adjacencyList[source]!!.filter { it.dest == dest }
        if (edges.isEmpty()) {
            return 0.0
        }

        check(edges.size == 1)
        val edge = edges.first()

        var result = 0.0

        for (symbolList in edge.edgeSymbols) {
            var product = 1.0
            var count = 0
            if (!symbolList.contains(action)) continue
            for (symbol in symbolList) {
                if (symbol != action) {
                    product *= symbolMapping[symbol]!!
                    count++
                }
            }
            check(count == symbolList.size - 1)
            result += product
        }

        return result
    }
}

interface EdgeProvider {

    fun getUniqueEdgesForState(state: UserEquipmentState): List<Edge>
}

