package stochastic.dtmc

import dtmc.symbol.Symbol
import policy.Action
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig

data class IndexIT(
    val source: UserEquipmentState,
    val dest: UserEquipmentState,
    val action: Action
)

data class SymbolFraction(
    val top: List<List<Symbol>>?,
    val bottom: List<List<Symbol>>?
) {

    fun resolveByMapping(symbolMapping: Map<Symbol, Double>): Double {
        if (top == null || bottom == null) {
            return 0.0
        }
        if (top.size == 1 && bottom.size == 1 && top[0].isEmpty() && bottom[0].isEmpty()) { // Going from (Q, 0, 0) to (Q, 0, 0) with No Operation
            return 1.0
        }
        check(top.all { it.isNotEmpty() })
        check(bottom.all { it.isNotEmpty() })

        return top.map { resolveListByMapping(it, symbolMapping) }.reduce { acc, d -> acc + d } /
                bottom.map { resolveListByMapping(it, symbolMapping) }.reduce { acc, d -> acc + d }
    }

    private fun resolveListByMapping(list: List<Symbol>, symbolMapping: Map<Symbol, Double>): Double {
        return list.map { symbolMapping[it]!! }.reduce { acc, d -> acc * d }
    }

    override fun toString(): String {
        if (top == null || bottom == null) {
            return "NullFraction"
        }

        return "$top / $bottom"
    }
}

// Calculates the following:
// X(s1, s2, k)
// Probability that the next step will be s2 given that we are in s1 and we have made decision k

class IndependentTransitionCalculator(
    private val stateConfig: UserEquipmentStateConfig
) {
    val creator: DTMCCreator = DTMCCreator(stateConfig)

    val itSymbols: MutableMap<IndexIT, SymbolFraction> = mutableMapOf()

    fun getIndependentTransitionFraction(source: UserEquipmentState, dest: UserEquipmentState, action: Action): SymbolFraction {
        val edges = creator.getUniqueEdgesForState(source)
        val allOutcomesWithSourceAndAction = mutableListOf<List<List<Symbol>>>()
        var destSymbols: List<List<Symbol>>? = null
        edges.forEach { edge ->
            if (edge.edgeSymbols.any { it.contains(action) }) {
                val addBottom = edge.edgeSymbols.filter { it.contains(action) }
                // println("addBottom = $addBottom")
                allOutcomesWithSourceAndAction.add(addBottom)

                if (edge.dest == dest) {
                    destSymbols = edge.edgeSymbols.filter { it.contains(action) }
                }
            }
        }

        if (destSymbols == null) {
            return SymbolFraction(null, null)
        } else {
            val top = destSymbols!!.map { symbolTerm -> symbolTerm.filter { it != action }}
            val bottom = allOutcomesWithSourceAndAction.flatten().map { symbolTerm -> symbolTerm.filter { it != action } }

            // if (top.any {it.isEmpty()}) { println("$source -> $dest with $action :: $destSymbols / ${allOutcomesWithSourceAndAction.flatten()}") }
            return SymbolFraction(
                top = top,
                bottom = bottom
            )
        }

    }
}