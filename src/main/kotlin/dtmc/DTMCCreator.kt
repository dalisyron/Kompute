package dtmc

import dtmc.transition.ParameterSymbol
import dtmc.transition.Symbol
import dtmc.transition.Transition
import policy.Action
import ue.UserEquipmentState
import ue.UserEquipmentStateConfig
import kotlin.math.max

class DTMCCreator(
    val stateConfig: UserEquipmentStateConfig,
) {
    private val stateManager: UserEquipmentStateManager = UserEquipmentStateManager(stateConfig)

    fun phi(z: Int): Int {
        return max(z - 1, 0)
    }

    fun getPossibleActions(state: UserEquipmentState): List<Action> {
        val (taskQueueLength, tuState, cpuState) = state

        val res = mutableListOf<Action>(Action.NoOperation)

        if (taskQueueLength >= 1) {
            if (tuState == 0) {
                res.add(Action.AddToTransmissionUnit)
            }
            if (cpuState == 0) {
                res.add(Action.AddToCPU)
            }
        }

        if (taskQueueLength >= 2) {
            if (tuState == 0 && cpuState == 0)
                res.add(Action.AddToTransmissionUnit)
        }

        return res
    }

    private fun getTransitionsForAction(state: UserEquipmentState, action: Action): List<Transition> {
        val isCpuActive = state.cpuState > 0

        val transitions = mutableListOf<Transition>()
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
                with(stateManager) { addToTransmissionUnitNextState(addToCPUNextState(state)) }
            }
        }.let {
            if (isCpuActive) stateManager.advanceCPUNextState(it) else it
        }.let {
            val destinations: List<Pair<UserEquipmentState, List<Symbol>>>
            if (it.taskQueueLength < stateConfig.taskQueueCapacity) {
                if (it.tuState == 0) {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(ParameterSymbol.AlphaC, action),
                        stateManager.addTaskNextState(it) to listOf(ParameterSymbol.Alpha, action)
                    )
                } else {
                    destinations = listOf<Pair<UserEquipmentState, List<Symbol>>>(
                        it to listOf(ParameterSymbol.AlphaC, ParameterSymbol.BetaC, action),
                        stateManager.addTaskNextState(it) to listOf(ParameterSymbol.Alpha, ParameterSymbol.BetaC, action),
                        stateManager.advanceTUNextState(it) to listOf(ParameterSymbol.AlphaC, ParameterSymbol.Beta, action),
                        stateManager.addTaskNextState(stateManager.advanceTUNextState(it)) to listOf(ParameterSymbol.Alpha, ParameterSymbol.Beta, action)
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
                Transition(state, entry.first, entry.second)
            }
        }
    }
}
