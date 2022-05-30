package core

import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.symbol.ParameterSymbol
import core.policy.Action
import stochastic.dtmc.EdgeProvider
import stochastic.dtmc.transition.Edge
import stochastic.dtmc.transition.Transition
import stochastic.dtmc.transition.toEdge
import java.lang.Integer.max

data class StateManagerConfig(
    val userEquipmentStateConfig: UserEquipmentStateConfig,
    val limitation: List<Limitation> = Limitation.None
) {

    val numberOfQueues: Int = userEquipmentStateConfig.numberOfQueues

    enum class Limitation {
        None, LocalOnly, OffloadOnly
    }
}

class UserEquipmentStateManager(private val config: StateManagerConfig) : PossibleActionProvider, EdgeProvider {
    private val userEquipmentStateConfig = config.userEquipmentStateConfig

    fun getNextStateRunningAction(sourceState: UserEquipmentState, action: Action): UserEquipmentState {
        when (action) {
            is Action.NoOperation -> {
                return sourceState
            }
            is Action.AddToCPU -> {
                return getNextStateAddingToCPU(sourceState, action.queueIndex)
            }
            is Action.AddToTransmissionUnit -> {
                return getNextStateAddingToTU(sourceState, action.queueIndex)
            }
            is Action.AddToBothUnits -> {
                return getNextStateAddingToBothUnits(
                    sourceState,
                    action.cpuTaskQueueIndex,
                    action.transmissionUnitTaskQueueIndex
                )
            }
        }
    }


    fun getNextStateAddingToBothUnits(
        sourceState: UserEquipmentState,
        cpuQueueIndex: Int,
        transmissionUnitTaskQueueIndex: Int
    ): UserEquipmentState {
        return getNextStateAddingToCPU(
            getNextStateAddingToTU(sourceState, transmissionUnitTaskQueueIndex),
            cpuQueueIndex
        )
    }

    fun getNextStateAddingToCPU(sourceState: UserEquipmentState, queueIndex: Int): UserEquipmentState {
        check(sourceState.cpuState == 0)
        check(sourceState.taskQueueLengths[queueIndex] > 0)

        return sourceState.copy(
            taskQueueLengths = sourceState.taskQueueLengths.decrementedAt(queueIndex),
            cpuState = -1
        )
    }

    fun getNextStateAddingToTU(sourceState: UserEquipmentState, queueIndex: Int): UserEquipmentState {
        check(sourceState.tuState == 0)
        check(sourceState.taskQueueLengths[queueIndex] > 0)

        return sourceState.copy(
            taskQueueLengths = sourceState.taskQueueLengths.decrementedAt(queueIndex),
            tuState = 1
        )
    }

    fun getNextStateAddingTaskToQueue(sourceState: UserEquipmentState, queueIndex: Int): UserEquipmentState {
        if (sourceState.taskQueueLengths[queueIndex] == userEquipmentStateConfig.taskQueueCapacity) {
            throw TaskQueueFullException()
        }
        require(sourceState.taskQueueLengths[queueIndex] in 0 until userEquipmentStateConfig.taskQueueCapacity)

        return sourceState.copy(
            taskQueueLengths = sourceState.taskQueueLengths.incrementedAt(queueIndex)
        )
    }

    fun getNextStateAdvancingCPU(sourceState: UserEquipmentState): UserEquipmentState {
        check(sourceState.cpuState > 0 || sourceState.cpuState == -1)
        val numberOfSections = userEquipmentStateConfig.cpuNumberOfSections[sourceState.cpuTaskTypeQueueIndex!!]

        if (sourceState.cpuState == numberOfSections - 1 || (sourceState.cpuState == -1 && numberOfSections == 1)) {
            return sourceState.copy(
                cpuState = 0,
                cpuTaskTypeQueueIndex = null
            )
        } else {
            return sourceState.copy(
                cpuState = max(sourceState.cpuState + 1, 1)
            )
        }
    }

    fun getNextStateAdvancingTU(sourceState: UserEquipmentState): UserEquipmentState {
        check(sourceState.tuState > 0)
        val numberOfPackets = userEquipmentStateConfig.tuNumberOfPackets[sourceState.tuTaskTypeQueueIndex!!]

        if (sourceState.tuState == numberOfPackets - 1) {
            return sourceState.copy(
                tuState = 0,
                tuTaskTypeQueueIndex = null
            )
        } else {
            return sourceState.copy(
                tuState = sourceState.tuState + 1
            )
        }
    }

    override fun getPossibleActions(state: UserEquipmentState): List<Action> {
        val result = mutableListOf<Action>(Action.NoOperation)

        if (state.isCPUActive() && state.isTUActive()) {
            return result
        }

        val nonEmptyQueueIndices = state.taskQueueLengths.indices.filter { state.taskQueueLengths[it] > 0 }

        if (!state.isCPUActive()) {
            for (queueIndex in nonEmptyQueueIndices) {
                result.add(Action.AddToCPU(queueIndex))
            }
        }

        if (!state.isTUActive()) {
            for (queueIndex in nonEmptyQueueIndices) {
                result.add(Action.AddToCPU(queueIndex))
            }
        }

        if (!state.isTUActive() && !state.isCPUActive()) {
            for (i in nonEmptyQueueIndices) {
                for (j in nonEmptyQueueIndices) {
                    if (i == j && state.taskQueueLengths[i] < 2) continue

                    result.add(
                        Action.AddToBothUnits(
                            cpuTaskQueueIndex = i,
                            transmissionUnitTaskQueueIndex = j
                        )
                    )
                }
            }
        }

        return result
    }

    fun getEdgesForState(state: UserEquipmentState): List<Edge> {
        return getPossibleActions(state)
            .map { action -> getTransitionsForAction(state, action) }
            .flatten().map { it.toEdge() }
    }

    override fun getUniqueEdgesForState(state: UserEquipmentState): List<Edge> {
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

    private fun checkStateAgainstLimitations(state: UserEquipmentState) {
        when (config.limitation) {
            StateManagerConfig.Limitation.LocalOnly -> {
                check(!state.isTUActive())
            }
            StateManagerConfig.Limitation.OffloadOnly -> {
                check(!state.isCPUActive())
            }
            else -> {}
        }
    }

    private fun getTransitionsForAction(state: UserEquipmentState, action: Action): List<Transition> {
        checkStateAgainstLimitations(state)
        val stateAfterAction = getNextStateRunningAction(state, action).let {
            if (it.isCPUActive()) getNextStateAdvancingCPU(it) else it
        }
        checkStateAgainstLimitations(stateAfterAction)
        val transitions: MutableList<Transition> = mutableListOf()
        val notFullIndices = (0 until config.numberOfQueues).filter {
            state.taskQueueLengths[it] < config.userEquipmentStateConfig.taskQueueCapacity
        }

        if (notFullIndices.isEmpty()) {
            if (stateAfterAction.isTUActive()) {
                transitions.add(
                    Transition(
                        source = state,
                        dest = getNextStateAdvancingTU(stateAfterAction),
                        transitionSymbols = listOf(listOf(action, ParameterSymbol.Beta))
                    )
                )
                transitions.add(
                    Transition(
                        source = state,
                        dest = stateAfterAction,
                        transitionSymbols = listOf(listOf(action, ParameterSymbol.BetaC))
                    )
                )
            } else {
                transitions.add(
                    Transition(
                        source = state,
                        dest = stateAfterAction,
                        transitionSymbols = listOf(listOf(action))
                    )
                )
            }
        } else {
            val taskArrivalMappings = getAllSubsets(notFullIndices.size)

            for (mapping in taskArrivalMappings) {
                val addTaskSymbols = mapping.mapIndexed { index, taskArrives ->
                    if (taskArrives) {
                        ParameterSymbol.Alpha(notFullIndices[index])
                    } else {
                        ParameterSymbol.AlphaC(notFullIndices[index])
                    }
                }

                if (stateAfterAction.isTUActive()) {
                    transitions.add(
                        Transition(
                            source = state,
                            dest = stateAfterAction,
                            transitionSymbols = listOf(listOf(action, ParameterSymbol.Beta) + addTaskSymbols)
                        )
                    )
                    transitions.add(
                        Transition(
                            source = state,
                            dest = stateAfterAction,
                            transitionSymbols = listOf(listOf(action, ParameterSymbol.BetaC) + addTaskSymbols)
                        )
                    )
                } else {
                    transitions.add(
                        Transition(
                            source = state,
                            dest = stateAfterAction,
                            transitionSymbols = listOf(listOf(action) + addTaskSymbols)
                        )
                    )
                }
            }
        }

        return transitions
    }

    fun isStatePossible(state: UserEquipmentState): Boolean {
        if (config.limitation == StateManagerConfig.Limitation.LocalOnly && state.isTUActive()) {
            return false
        }

        if (config.limitation == StateManagerConfig.Limitation.OffloadOnly && state.isCPUActive()) {
            return false
        }

        return true
    }
}

class TaskQueueFullException : IllegalStateException()

interface PossibleActionProvider {
    fun getPossibleActions(state: UserEquipmentState): List<Action>
}

