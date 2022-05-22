package stochastic.dtmc

import dtmc.UserEquipmentStateManager
import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import policy.Action
import stochastic.dtmc.transition.Edge
import stochastic.dtmc.transition.Transition
import stochastic.dtmc.transition.toEdge
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.ue.UserEquipmentStateConfig.Companion.allStates
import dtmc.PossibleActionProvider

data class DiscreteTimeMarkovChain(
    val stateConfig: UserEquipmentStateConfig,
    val startState: UserEquipmentState,
    val adjacencyList: Map<UserEquipmentState, List<Edge>>
)

class DTMCCreator(
    private val stateConfig: UserEquipmentStateConfig,
    private val stateManager: UserEquipmentStateManager = UserEquipmentStateManager(stateConfig)
) : PossibleActionProvider by stateManager {

    fun create(): DiscreteTimeMarkovChain {
        val adjacencyList: Map<UserEquipmentState, List<Edge>> =
            stateConfig.allStates().associateWith { getUniqueEdgesForState(it) }

        return DiscreteTimeMarkovChain(
            stateConfig = stateConfig,
            startState = UserEquipmentState(0, 0, 0),
            adjacencyList = adjacencyList
        )
    }


    fun getEdgesForState(state: UserEquipmentState): List<Edge> {
        return getPossibleActions(state)
            .map { action -> getTransitionsForAction(state, action) }
            .flatten().map { it.toEdge() }
    }

    fun getUniqueEdgesForState(state: UserEquipmentState): List<Edge> {
        val edges = getEdgesForState(state)

        val uniqueEdges: List<Edge> = edges.groupBy { edge -> edge.dest }
            .mapValues {
                val (dest: UserEquipmentState, edgeList: List<Edge>) = it
                Edge(
                    dest = dest,
                    edgeSymbols = edgeList.map { edge -> edge.edgeSymbols }.flatten()
                )
            }
            .values
            .toList()

        return uniqueEdges
    }

    private fun getTransitionsForAction(state: UserEquipmentState, action: Action): List<Transition> {

        when (action) {
            Action.NoOperation -> {
                state
            }
            Action.AddToCPU -> {
                stateManager.addToCPUNextState(state)
            }
            Action.AddToTransmissionUnit -> {
                stateManager.addToTransmissionUnitNextState(state)
            }
            Action.AddToBothUnits -> {
                check(state.taskQueueLength > 1)
                stateManager.addToTransmissionUnitNextState(stateManager.addToCPUNextState(state))
            }
        }.let {
            stateManager.advanceCPUIfActiveNextState(it)
        }.let {
            val destinations: List<Pair<UserEquipmentState, List<Symbol>>>
            if (it.taskQueueLength < stateConfig.taskQueueCapacity) {
                if (it.tuState == 0) {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf<Symbol>(ParameterSymbol.AlphaC, action),
                        stateManager.addTaskNextState(it) to listOf(ParameterSymbol.Alpha, action)
                    )
                } else {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(ParameterSymbol.AlphaC, ParameterSymbol.BetaC, action),
                        stateManager.addTaskNextState(it) to listOf(
                            ParameterSymbol.Alpha,
                            ParameterSymbol.BetaC,
                            action
                        ),
                        stateManager.advanceTUNextState(it) to listOf(
                            ParameterSymbol.AlphaC,
                            ParameterSymbol.Beta,
                            action
                        ),
                        stateManager.addTaskNextState(stateManager.advanceTUNextState(it)) to listOf(
                            ParameterSymbol.Alpha,
                            ParameterSymbol.Beta,
                            action
                        )
                    )
                }
            } else {
                if (it.tuState == 0) {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(action)
                    )
                } else {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(ParameterSymbol.BetaC, action),
                        stateManager.advanceTUNextState(it) to listOf(ParameterSymbol.Beta, action)
                    )
                }
            }

            return destinations.map { entry ->
                Transition(state, entry.first, listOf(entry.second))
            }
        }
    }
}
