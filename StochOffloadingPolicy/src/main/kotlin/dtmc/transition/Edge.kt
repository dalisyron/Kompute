package dtmc.transition

import dtmc.symbol.Symbol
import ue.UserEquipmentState

data class Edge(
    val dest: UserEquipmentState,
    val edgeSymbols: List<List<Symbol>>
)