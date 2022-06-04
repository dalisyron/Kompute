package stochastic.dtmc

import core.symbol.Symbol
import core.policy.Action
import core.ue.UserEquipmentState
import stochastic.dtmc.transition.Edge

interface EdgeProvider {

    fun getUniqueEdgesForState(state: UserEquipmentState): List<Edge>
}

