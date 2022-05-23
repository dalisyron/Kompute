package stochastic.dtmc

import core.UserEquipmentStateManager
import stochastic.dtmc.transition.Edge
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.ue.UserEquipmentStateConfig.Companion.allStates
import core.PossibleActionProvider

data class DiscreteTimeMarkovChain(
    val stateConfig: UserEquipmentStateConfig,
    val startState: UserEquipmentState,
    val adjacencyList: Map<UserEquipmentState, List<Edge>>
)

class DTMCCreator(
    private val stateConfig: UserEquipmentStateConfig,
) {
    private val stateManager: UserEquipmentStateManager = UserEquipmentStateManager(stateConfig)

    fun create(): DiscreteTimeMarkovChain {
        val adjacencyList: Map<UserEquipmentState, List<Edge>> =
            stateConfig.allStates().associateWith { stateManager.getUniqueEdgesForState(it) }

        return DiscreteTimeMarkovChain(
            stateConfig = stateConfig,
            startState = UserEquipmentState(0, 0, 0),
            adjacencyList = adjacencyList
        )
    }
}
