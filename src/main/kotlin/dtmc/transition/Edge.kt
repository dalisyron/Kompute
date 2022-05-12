package dtmc.transition

import ue.UserEquipmentState

data class Edge(
    val dest: UserEquipmentState,
    val edgeSymbols: List<List<Symbol>>
)