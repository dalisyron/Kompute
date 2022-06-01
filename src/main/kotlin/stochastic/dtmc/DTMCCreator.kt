package stochastic.dtmc

import core.UserEquipmentStateManager
import stochastic.dtmc.transition.Edge
import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
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
            stateManager.allStates()
                .associateWith { stateManager.getUniqueEdgesForState(it) }

        return DiscreteTimeMarkovChain(
            stateConfig = stateManagerConfig.userEquipmentStateConfig,
            startState = stateManager.getInitialState(),
            adjacencyList = adjacencyList
        )
    }
}
