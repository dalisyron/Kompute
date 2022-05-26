package core

import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import dtmc.symbol.ParameterSymbol
import dtmc.symbol.Symbol
import policy.Action
import stochastic.dtmc.EdgeProvider
import stochastic.dtmc.transition.Edge
import stochastic.dtmc.transition.Transition
import stochastic.dtmc.transition.toEdge
import ue.UserEquipmentState.Companion.validate

data class StateManagerConfig(
    val userEquipmentStateConfig: UserEquipmentStateConfig,
    val limitation: Limitation = Limitation.None
) {

    enum class Limitation {
        None, LocalOnly, OffloadOnly
    }
}

class UserEquipmentStateManager(private val config: StateManagerConfig) : PossibleActionProvider, EdgeProvider {
    private val userEquipmentStateConfig = config.userEquipmentStateConfig

    fun addTaskNextState(state: UserEquipmentState): UserEquipmentState {
        // TODO test this
        if (state.taskQueueLength == userEquipmentStateConfig.taskQueueCapacity) {
            throw TaskQueueFullException()
        }
        require(state.taskQueueLength in 0 until userEquipmentStateConfig.taskQueueCapacity)

        return state.copy(
            taskQueueLength = state.taskQueueLength + 1
        )
    }

    fun advanceCPUNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.cpuState >= 0)
        val nextCpuState: Int = if (state.cpuState == userEquipmentStateConfig.cpuNumberOfSections - 1) 0 else state.cpuState + 1

        return state.copy(
            cpuState = nextCpuState
        )
    }

    fun advanceTUNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.tuState > 0)
        val nextTuState: Int = if (state.tuState == userEquipmentStateConfig.tuNumberOfPackets) 0 else state.tuState + 1
        return state.copy(
            tuState = nextTuState
        )
    }

    fun addToTransmissionUnitNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.tuState == 0)

        return state.copy(
            taskQueueLength = state.taskQueueLength - 1,
            tuState = 1
        )
    }

    fun addToCPUNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.cpuState == 0)

        return state.copy(
            taskQueueLength = state.taskQueueLength - 1,
            cpuState = -1
        )
    }

    fun advanceCPUIfActiveNextState(state: UserEquipmentState): UserEquipmentState {
        return if (state.cpuState > 0) {
            advanceCPUNextState(state)
        } else if (state.cpuState == -1) {
            state.copy(
                cpuState = 1
            )
        } else if (state.cpuState == 0) {
            state
        } else {
            throw IllegalStateException()
        }
    }

    override fun getPossibleActions(state: UserEquipmentState): List<Action> {
        val (taskQueueLength, tuState, cpuState) = state

        val res = mutableListOf<Action>(Action.NoOperation)

        if (taskQueueLength >= 1) {
            if (tuState == 0 && config.limitation != StateManagerConfig.Limitation.LocalOnly) {
                res.add(Action.AddToTransmissionUnit)
            }
            if (cpuState == 0 && config.limitation != StateManagerConfig.Limitation.OffloadOnly) {
                res.add(Action.AddToCPU)
            }
        }

        if (taskQueueLength >= 2) {
            if (tuState == 0 && cpuState == 0 && config.limitation == StateManagerConfig.Limitation.None)
                res.add(Action.AddToBothUnits)
        }

        return res.sortedBy { it.order }
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

    private fun getTransitionsForAction(state: UserEquipmentState, action: Action): List<Transition> {

        when (action) {
            Action.NoOperation -> {
                state
            }
            Action.AddToCPU -> {
                addToCPUNextState(state)
            }
            Action.AddToTransmissionUnit -> {
                addToTransmissionUnitNextState(state)
            }
            Action.AddToBothUnits -> {
                check(state.taskQueueLength > 1)
                addToTransmissionUnitNextState(addToCPUNextState(state))
            }
        }.let {
            advanceCPUIfActiveNextState(it)
        }.let {
            val destinations: List<Pair<UserEquipmentState, List<Symbol>>>
            if (it.taskQueueLength < userEquipmentStateConfig.taskQueueCapacity) {
                if (it.tuState == 0) {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf<Symbol>(ParameterSymbol.AlphaC, action),
                        addTaskNextState(it) to listOf(ParameterSymbol.Alpha, action)
                    )
                } else {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(ParameterSymbol.AlphaC, ParameterSymbol.BetaC, action),
                        addTaskNextState(it) to listOf(
                            ParameterSymbol.Alpha,
                            ParameterSymbol.BetaC,
                            action
                        ),
                        advanceTUNextState(it) to listOf(
                            ParameterSymbol.AlphaC,
                            ParameterSymbol.Beta,
                            action
                        ),
                        addTaskNextState(advanceTUNextState(it)) to listOf(
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
                        advanceTUNextState(it) to listOf(ParameterSymbol.Beta, action)
                    )
                }
            }

            return destinations.map { entry ->
                Transition(state, entry.first, listOf(entry.second))
            }
        }
    }

    fun isStatePossible(state: UserEquipmentState): Boolean {
        if (config.limitation == StateManagerConfig.Limitation.LocalOnly && state.tuState > 0) {
            return false
        }

        if (config.limitation == StateManagerConfig.Limitation.OffloadOnly && state.cpuState > 0) {
            return false
        }

        return true
    }

    class TaskQueueFullException : IllegalStateException()
}

interface PossibleActionProvider {
    fun getPossibleActions(state: UserEquipmentState): List<Action>
}

