package core.ue

import core.compareTo

data class UserEquipmentState(
    val taskQueueLengths: List<Int>,
    val tuState: Int,
    val cpuState: Int,
    val tuTaskTypeQueueIndex: Int,
    val cpuTaskTypeQueueIndex: Int
) : Comparable<UserEquipmentState> {

    override fun compareTo(other: UserEquipmentState): Int {
        taskQueueLengths.compareTo(other.taskQueueLengths).let {
            if (it != 0) {
                return it
            }
        }

        tuState.compareTo(other.tuState).let {
            if (it != 0) {
                return it
            }
        }

        cpuState.compareTo(other.cpuState).let {
            if (it != 0) {
                return it
            }
        }

        (tuTaskTypeQueueIndex).compareTo(other.tuTaskTypeQueueIndex).let {
            if (it != 0) {
                return it
            }
        }

        return (cpuTaskTypeQueueIndex).compareTo(other.cpuTaskTypeQueueIndex)
    }

    override fun toString(): String {
        return "{ [${taskQueueLengths.joinToString(", ")}], $tuState, ${cpuState}, $tuTaskTypeQueueIndex, $cpuTaskTypeQueueIndex }"
    }

    fun isCPUActive(): Boolean {
        return cpuState > 0 || cpuState == -1
    }

    fun isTUActive(): Boolean {
        return tuState > 0
    }

    companion object {

        fun singleQueue(
            taskQueueLengths: Int,
            tuState: Int,
            cpuState: Int
        ): UserEquipmentState {
            return UserEquipmentState(
                taskQueueLengths = listOf(taskQueueLengths),
                tuState = tuState,
                cpuState = cpuState,
                tuTaskTypeQueueIndex = -1,
                cpuTaskTypeQueueIndex = -1
            )
        }
    }
}