package stochastic.dtmc

import dtmc.symbol.Symbol
import policy.Action
import ue.UserEquipmentState
import ue.UserEquipmentStateConfig
import ue.UserEquipmentStateConfig.Companion.allStates

data class IndexIT(
    val source: UserEquipmentState,
    val dest: UserEquipmentState,
    val action: Action
)

data class SymbolFraction(
    val top: List<List<Symbol>>?,
    val bottom: List<List<Symbol>>?
)

// Calculates the following:
// X(s1, s2, k)
// Probability that the next step will be s2 given that we are in s1 and we have made decision k

class IndependentTransitionCalculator(
    private val stateConfig: UserEquipmentStateConfig
) {
    val allActions = listOf(
        Action.NoOperation,
        Action.AddToCPU,
        Action.AddToTransmissionUnit,
        Action.AddToBothUnits
    )
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
            return SymbolFraction(
                top = destSymbols!!.map { symbolTerm -> symbolTerm.filter { it != action }},
                bottom = allOutcomesWithSourceAndAction.flatten().map { symbolTerm -> symbolTerm.filter { it != action } }
            )
        }

    }

    fun calculate() {
        forAllTriplets { source: UserEquipmentState, dest: UserEquipmentState, action: Action ->
            val indexIT = IndexIT(source, dest, action)
            itSymbols[indexIT] = getIndependentTransitionFraction(source, dest, action)
        }
    }

    fun forAllTriplets(block: (source: UserEquipmentState, dest: UserEquipmentState, action: Action) -> Unit) {
        stateConfig.allStates().forEach { source ->
            stateConfig.allStates().forEach { dest ->
                allActions.forEach { action ->
                    block(source, dest, action)
                }
            }
        }
    }
}