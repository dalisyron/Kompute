package dtmc.transition

import ue.UserEquipmentState

class Transition(
    val source: UserEquipmentState,
    val dest: UserEquipmentState,
    val edgeSymbols: List<Symbol>
)