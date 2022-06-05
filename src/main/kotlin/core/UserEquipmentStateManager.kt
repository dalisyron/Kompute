package core

import core.ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import core.symbol.ParameterSymbol
import core.policy.Action
import core.ue.OffloadingSystemConfig
import stochastic.dtmc.EdgeProvider
import stochastic.dtmc.transition.Edge
import stochastic.dtmc.transition.Transition
import stochastic.dtmc.transition.toEdge
import java.lang.Integer.max

data class StateManagerConfig(
    val userEquipmentStateConfig: UserEquipmentStateConfig,
    val limitation: List<Limitation>
) {

    val numberOfQueues: Int = userEquipmentStateConfig.numberOfQueues

    enum class Limitation {
        None, LocalOnly, OffloadOnly
    }

    companion object {

        fun singleQueue(
            userEquipmentStateConfig: UserEquipmentStateConfig,
            limitation: Limitation = Limitation.None
        ): StateManagerConfig {
            require(userEquipmentStateConfig.numberOfQueues == 1)
            return StateManagerConfig(
                userEquipmentStateConfig = userEquipmentStateConfig,
                limitation = listOf(limitation)
            )
        }
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
        if (cpuQueueIndex == transmissionUnitTaskQueueIndex) {
            check(sourceState.taskQueueLengths[cpuQueueIndex] > 1)
        } else {
            check(sourceState.taskQueueLengths[cpuQueueIndex] > 0 && sourceState.taskQueueLengths[transmissionUnitTaskQueueIndex] > 0)
        }
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
            cpuState = -1,
            cpuTaskTypeQueueIndex = queueIndex
        )
    }

    fun getNextStateAddingToTU(sourceState: UserEquipmentState, queueIndex: Int): UserEquipmentState {
        check(sourceState.tuState == 0)
        check(sourceState.taskQueueLengths[queueIndex] > 0)

        return sourceState.copy(
            taskQueueLengths = sourceState.taskQueueLengths.decrementedAt(queueIndex),
            tuState = 1,
            tuTaskTypeQueueIndex = queueIndex
        )
    }

    fun getNextStateAddingTaskToQueue(sourceState: UserEquipmentState, queueIndex: Int): UserEquipmentState {
        if (sourceState.taskQueueLengths[queueIndex] == userEquipmentStateConfig.taskQueueCapacity) {
            throw TaskQueueFullException()
        }
        require(sourceState.taskQueueLengths[queueIndex] in 0 until userEquipmentStateConfig.taskQueueCapacity) {
            "${sourceState.taskQueueLengths[queueIndex]} | ${userEquipmentStateConfig.taskQueueCapacity}"
        }

        return sourceState.copy(
            taskQueueLengths = sourceState.taskQueueLengths.incrementedAt(queueIndex)
        )
    }

    fun getNextStateAdvancingCPU(sourceState: UserEquipmentState): UserEquipmentState {
        check(sourceState.cpuState > 0 || sourceState.cpuState == -1)
        val numberOfSections = userEquipmentStateConfig.cpuNumberOfSections[sourceState.cpuTaskTypeQueueIndex]

        if (sourceState.cpuState == numberOfSections - 1 || (sourceState.cpuState == -1 && numberOfSections == 1)) {
            return sourceState.copy(
                cpuState = 0,
                cpuTaskTypeQueueIndex = -1
            )
        } else {
            return sourceState.copy(
                cpuState = max(sourceState.cpuState + 1, 1)
            )
        }
    }

    fun getNextStateAdvancingTU(sourceState: UserEquipmentState): UserEquipmentState {
        check(sourceState.tuState > 0)
        val numberOfPackets = userEquipmentStateConfig.tuNumberOfPackets[sourceState.tuTaskTypeQueueIndex]

        if (sourceState.tuState == numberOfPackets) {
            return sourceState.copy(
                tuState = 0,
                tuTaskTypeQueueIndex = -1
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
                if (config.limitation[queueIndex] != StateManagerConfig.Limitation.OffloadOnly) {
                    result.add(Action.AddToCPU(queueIndex))
                }
            }
        }

        if (!state.isTUActive()) {
            for (queueIndex in nonEmptyQueueIndices) {
                if (config.limitation[queueIndex] != StateManagerConfig.Limitation.LocalOnly) {
                    result.add(Action.AddToTransmissionUnit(queueIndex))
                }
            }
        }

        if (!state.isTUActive() && !state.isCPUActive()) {
            for (i in nonEmptyQueueIndices) {
                for (j in nonEmptyQueueIndices) {
                    if (i == j && state.taskQueueLengths[i] < 2) continue

                    if (config.limitation[i] != StateManagerConfig.Limitation.OffloadOnly && config.limitation[j] != StateManagerConfig.Limitation.LocalOnly) {
                        result.add(
                            Action.AddToBothUnits(
                                cpuTaskQueueIndex = i,
                                transmissionUnitTaskQueueIndex = j
                            )
                        )
                    }
                }
            }
        }

        return result.sorted()
    }

    fun getEdgesForState(state: UserEquipmentState): List<Edge> {
        return getPossibleActions(state)
            .map { action ->
                getTransitionsForAction(state, action)
            }
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
        if (config.limitation.all { it == StateManagerConfig.Limitation.LocalOnly }) {
            check(!state.isTUActive())
        }
        if (config.limitation.all { it == StateManagerConfig.Limitation.OffloadOnly }) {
            check(!state.isCPUActive()) {
                state
            }
        }
    }

    fun getTransitionsForAction(state: UserEquipmentState, action: Action): List<Transition> {
        checkStateAgainstLimitations(state)
        val stateAfterAction = getNextStateRunningAction(state, action).let {
            if (it.isCPUActive()) getNextStateAdvancingCPU(it) else it
        }
        checkStateAgainstLimitations(stateAfterAction)
        val transitions: MutableList<Transition> = mutableListOf()

        val notFullIndicesAfterAction = (stateAfterAction.taskQueueLengths.indices).filter {
            stateAfterAction.taskQueueLengths[it] < config.userEquipmentStateConfig.taskQueueCapacity
        }

        if (notFullIndicesAfterAction.isEmpty()) {
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
            val taskArrivalMappings = getAllSubsets(notFullIndicesAfterAction.size)

            for (mapping in taskArrivalMappings) {
                val addTaskSymbols = mapping.mapIndexed { index, taskArrives ->
                    if (taskArrives) {
                        ParameterSymbol.Alpha(notFullIndicesAfterAction[index])
                    } else {
                        ParameterSymbol.AlphaC(notFullIndicesAfterAction[index])
                    }
                }
                val destState = getNextStateAddingTasksBasedOnMapping(stateAfterAction, mapping, notFullIndicesAfterAction)

                if (stateAfterAction.isTUActive()) {
                    transitions.add(
                        Transition(
                            source = state,
                            dest = getNextStateAdvancingTU(destState),
                            transitionSymbols = listOf(listOf(action, ParameterSymbol.Beta) + addTaskSymbols)
                        )
                    )
                    transitions.add(
                        Transition(
                            source = state,
                            dest = destState,
                            transitionSymbols = listOf(listOf(action, ParameterSymbol.BetaC) + addTaskSymbols)
                        )
                    )
                } else {
                    transitions.add(
                        Transition(
                            source = state,
                            dest = destState,
                            transitionSymbols = listOf(listOf(action) + addTaskSymbols)
                        )
                    )
                }
            }
        }

        return transitions
    }

    private fun getNextStateAddingTasksBasedOnMapping(
        sourceState: UserEquipmentState,
        taskArrivalMapping: List<Boolean>,
        notFullIndicesAfterAction: List<Int>
    ): UserEquipmentState {
        require(taskArrivalMapping.size == notFullIndicesAfterAction.size) {
            "$taskArrivalMapping | ${config.numberOfQueues}"
        }

        var startState = sourceState

        for (i in taskArrivalMapping.indices) {
            if (taskArrivalMapping[i]) {
                startState = getNextStateAddingTaskToQueue(startState, notFullIndicesAfterAction[i])
            }
        }

        return startState
    }

    fun isStatePossible(state: UserEquipmentState): Boolean {
        try {
            checkStateAgainstLimitations(state)
            return true
        } catch (e: IllegalStateException) {
            return false
        }
    }

    fun getInitialState(): UserEquipmentState {
        return UserEquipmentState(
            taskQueueLengths = (1..userEquipmentStateConfig.numberOfQueues).map { 0 },
            tuState = 0,
            cpuState = 0,
            tuTaskTypeQueueIndex = -1,
            cpuTaskTypeQueueIndex = -1
        )
    }

    fun allStates(): List<UserEquipmentState> {
        val states: List<UserEquipmentState> = allStatesUnchecked()

        val possibleStates = states.filterNot {
            val badA = !it.isCPUActive() && (it.cpuTaskTypeQueueIndex != -1)
            val badB = !it.isTUActive() && (it.tuTaskTypeQueueIndex != -1)
            val badC = it.isCPUActive() && (it.cpuTaskTypeQueueIndex == -1)
            val badD = it.isTUActive() && (it.tuTaskTypeQueueIndex == -1)

            return@filterNot badA || badB || badC || badD
        }.filterNot { it ->
            val badA = (it.isCPUActive() && config.limitation.all { it == StateManagerConfig.Limitation.OffloadOnly })
            val badB = (it.isTUActive() && config.limitation.all { it == StateManagerConfig.Limitation.LocalOnly })
            return@filterNot badA || badB
        }.filter { state ->
            if (state.isCPUActive()) {
                val numberOfSections = config.userEquipmentStateConfig.cpuNumberOfSections[state.cpuTaskTypeQueueIndex]
                return@filter state.cpuState < numberOfSections
            }
            true
        }.filter { state ->
            if (state.isTUActive()) {
                val numberOfPackets = config.userEquipmentStateConfig.tuNumberOfPackets[state.tuTaskTypeQueueIndex]
                return@filter state.tuState <= numberOfPackets
            }
            true
        }
        return possibleStates.sorted()
    }

    fun allStatesUnchecked(): List<UserEquipmentState> {
        val dimensions: List<Int> =
            (1..config.numberOfQueues).map { config.userEquipmentStateConfig.taskQueueCapacity + 1 } + listOf(config.userEquipmentStateConfig.tuNumberOfPackets.maxOrNull()!! + 1) + listOf(
                config.userEquipmentStateConfig.cpuNumberOfSections.maxOrNull()!!
            ) + listOf(config.numberOfQueues + 1) + listOf(config.numberOfQueues + 1)

        val states: List<UserEquipmentState> = TupleGenerator.generateTuples(dimensions).map { tuple ->
            val cpuTaskTypeQueueIndex: Int = tuple[tuple.size - 1] - 1
            val tuTaskTypeQueueIndex: Int = tuple[tuple.size - 2] - 1

            UserEquipmentState(
                taskQueueLengths = tuple.subList(0, config.numberOfQueues),
                tuState = tuple[config.numberOfQueues],
                cpuState = tuple[config.numberOfQueues + 1],
                tuTaskTypeQueueIndex = tuTaskTypeQueueIndex,
                cpuTaskTypeQueueIndex = cpuTaskTypeQueueIndex
            )
        }

        return states.sorted()
    }

    companion object {

        fun fromSystemConfig(offloadingSystemConfig: OffloadingSystemConfig): UserEquipmentStateManager {
            return UserEquipmentStateManager(
                config = StateManagerConfig(
                    userEquipmentStateConfig = offloadingSystemConfig.stateConfig,
                    limitation = offloadingSystemConfig.getLimitation()
                )
            )
        }

        fun getAllStatesForConfig(systemConfig: OffloadingSystemConfig): List<UserEquipmentState> {
            val manager = fromSystemConfig(systemConfig)
            return manager.allStates()
        }
    }
}

class TaskQueueFullException : IllegalStateException()

interface PossibleActionProvider {
    fun getPossibleActions(state: UserEquipmentState): List<Action>
}

