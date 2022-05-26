package stochastic.dtmc

import core.UserEquipmentStateManager
import stochastic.dtmc.transition.Edge
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.ue.UserEquipmentStateConfig.Companion.allStates
import core.StateManagerConfig

data class DiscreteTimeMarkovChain(
    val stateConfig: UserEquipmentStateConfig,
    val startState: UserEquipmentState,
    val adjacencyList: Map<UserEquipmentState, List<Edge>>
)

class DTMCCreator(
    private val stateManagerConfig: StateManagerConfig
) {
    private val stateManager: UserEquipmentStateManager = UserEquipmentStateManager(stateManagerConfig)

    fun create(): DiscreteTimeMarkovChain {
        val adjacencyList: Map<UserEquipmentState, List<Edge>> =
            stateManagerConfig.userEquipmentStateConfig.allStates()
                .associateWith { stateManager.getUniqueEdgesForState(it) }

        return DiscreteTimeMarkovChain(
            stateConfig = stateManagerConfig.userEquipmentStateConfig,
            startState = UserEquipmentState(0, 0, 0),
            adjacencyList = adjacencyList
        )
    }
}
