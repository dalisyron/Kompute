package stochastic.dtmc.transition

import core.symbol.Symbol
import core.ue.UserEquipmentState

data class Edge(
    val dest: UserEquipmentState,
    val edgeSymbols: List<List<Symbol>>
)