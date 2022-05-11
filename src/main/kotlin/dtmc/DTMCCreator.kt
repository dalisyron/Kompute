/*
package dtmc

import policy.Action
import ue.UserEquipmentConfig
import ue.UserEquipmentState
import ue.UserEquipmentState.Companion.validate
import kotlin.math.max
import kotlin.random.Random

class DTMCCreator(
    private val config: UserEquipmentConfig
) {

    fun phi(z: Int): Int {
        return max(z - 1, 0)
    }

    fun getPossibleActions(state: UserEquipmentState): List<Action> {
        state.validate()
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

    private fun getOutcomesForAction(state: UserEquipmentState, action: Action): UserEquipmentState {
        state
        val isCpuActive = state.cpuState > 0

        return when (action) {
            Action.NoOperation -> {
                state
            }
            Action.AddToCPU -> {
                addToCPU(state)
            }
            Action.AddToTransmissionUnit -> {
                addToTransmissionUnit(state)
            }
            Action.AddToBothUnits -> {
                check(state.taskQueueLength > 1)
                addToTransmissionUnit(addToCPU(state))
            }
        }.let {
            if (isCpuActive) advanceCPUInState(it) else it
        }.let {
            val rand = Random.nextDouble()
            if (it.tuState > 0 && rand < config.beta) advanceTUInState(it) else it
        }.let {
            val rand = Random.nextDouble()
            if (rand < config.alpha) addTask(it) else it
        }
    }

 */