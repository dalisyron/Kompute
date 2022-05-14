package stochastic.dtmc.transition

import dtmc.symbol.Symbol
import ue.UserEquipmentState

data class Transition(
    val source: UserEquipmentState,
    val dest: UserEquipmentState,
    val transitionSymbols: List<List<Symbol>>
)

fun Transition.toEdge(): Edge {
    return Edge(dest = dest, edgeSymbols = transitionSymbols)
}