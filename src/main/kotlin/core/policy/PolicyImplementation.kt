package core.policy

object LocalOnlyPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax || state.cpuState != 0) {
            return Action.NoOperation
        }

        for (queueIndex in state.taskQueueLength.indices) {
            if (state.taskQueueLength[queueIndex] > 0) {
                return Action.AddToCPU(queueIndex)
            }
        }

        return Action.NoOperation
    }
}

object OffloadOnlyPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax || state.tuState != 0) {
            return Action.NoOperation
        }

        for (queueIndex in state.taskQueueLength.indices) {
            if (state.taskQueueLength[queueIndex] > 0) {
                return Action.AddToTransmissionUnit(queueIndex)
            }
        }

        return Action.NoOperation
    }
}

abstract class GreedyPolicy : Policy {

    fun getActionForStateGreedy(state: UserEquipmentExecutionState, singleTaskAction: (Int) -> Action): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        if (state.ueState.isCPUActive() && state.ueState.isTUActive()) {
            return Action.NoOperation
        }

        if (state.taskQueueLength.all { it == 0 }) {
            return Action.NoOperation
        }

        if (state.ueState.isCPUActive()) {
            return OffloadOnlyPolicy.getActionForState(state)
        }

        if (state.ueState.isTUActive()) {
            return LocalOnlyPolicy.getActionForState(state)
        }

        val nonEmptyIndices = state.taskQueueLength.indices.filter { state.taskQueueLength[it] > 0 }
        check(nonEmptyIndices.isNotEmpty())

        val queueIndices: Pair<Int, Int>? = state.ueState.getTwoRandomQueueIndicesForTwoTasks()

        if (queueIndices == null) {
            check(nonEmptyIndices.size == 1)
            return singleTaskAction(nonEmptyIndices[0])
        } else {
            return Action.AddToBothUnits(
                cpuTaskQueueIndex = queueIndices.first,
                transmissionUnitTaskQueueIndex = queueIndices.second
            )
        }
    }
}

object GreedyLocalFirstPolicy : GreedyPolicy() {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        return getActionForStateGreedy(state) { queueIndex ->
            Action.AddToCPU(queueIndex)
        }
    }
}

object GreedyOffloadFirstPolicy : GreedyPolicy() {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        return getActionForStateGreedy(state) { queueIndex ->
            Action.AddToTransmissionUnit(queueIndex)
        }
    }
}