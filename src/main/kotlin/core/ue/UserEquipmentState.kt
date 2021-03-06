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

    fun getTwoRandomQueueIndicesForTwoTasks(): Pair<Int, Int>? {
        val nonEmptyQueueIndices = taskQueueLengths.indices.filter {
            taskQueueLengths[it] > 0
        }

        if (nonEmptyQueueIndices.size == 1) {
            if (taskQueueLengths[nonEmptyQueueIndices[0]] == 1) {
                return null
            } else {
                return nonEmptyQueueIndices.first() to nonEmptyQueueIndices.first()
            }
        }

        val options: MutableList<Pair<Int, Int>> = mutableListOf()

        for (i in nonEmptyQueueIndices) {
            for (j in nonEmptyQueueIndices) {
                if (i != j || taskQueueLengths[i] > 1) {
                    options.add(i to j)
                }
            }
        }

        return options.random()
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
                tuTaskTypeQueueIndex = if (tuState == 0) -1 else 0,
                cpuTaskTypeQueueIndex = if (cpuState == 0) -1 else 0
            )
        }
    }
}