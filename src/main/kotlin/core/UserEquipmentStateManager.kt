package dtmc

import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig
import policy.Action

class UserEquipmentStateManager(private val config: UserEquipmentStateConfig) : PossibleActionProvider {

    fun addTaskNextState(state: UserEquipmentState): UserEquipmentState {
        // TODO test this
        if (state.taskQueueLength == config.taskQueueCapacity) {
            throw TaskQueueFullException()
        }
        require(state.taskQueueLength in 0 until config.taskQueueCapacity)

        return state.copy(
            taskQueueLength = state.taskQueueLength + 1
        )
    }

    fun advanceCPUNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.cpuState >= 0)
        val nextCpuState: Int = if (state.cpuState == config.cpuNumberOfSections - 1) 0 else state.cpuState + 1

        return state.copy(
            cpuState = nextCpuState
        )
    }

    fun advanceTUNextState(state: UserEquipmentState): UserEquipmentState {
        check(state.tuState > 0)
        val nextTuState: Int = if (state.tuState == config.tuNumberOfPackets) 0 else state.tuState + 1
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
            if (tuState == 0) {
                res.add(Action.AddToTransmissionUnit)
            }
            if (cpuState == 0) {
                res.add(Action.AddToCPU)
            }
        }

        if (taskQueueLength >= 2) {
            if (tuState == 0 && cpuState == 0)
                res.add(Action.AddToBothUnits)
        }

        return res
    }

    class TaskQueueFullException : IllegalStateException()
}

interface PossibleActionProvider {
    fun getPossibleActions(state: UserEquipmentState): List<Action>
}

