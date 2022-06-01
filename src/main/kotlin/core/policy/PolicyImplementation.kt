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

object TransmitOnlyPolicy : Policy {

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

object GreedyLocalFirstPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        if (state.cpuState != 0 && state.tuState != 0) {
            return Action.NoOperation
        }
        if (state.cpuState != 0) {
            return TransmitOnlyPolicy.getActionForState(state)
        }
        if (state.tuState != 0) {
            return LocalOnlyPolicy.getActionForState(state)
        }

        val nonEmptyQueueIndices = state.taskQueueLength.mapIndexed { index, i ->
            if (state.taskQueueLength[index] > 0) index else null
        }.filterNotNull()

        if (nonEmptyQueueIndices.isEmpty()) {
            return Action.NoOperation
        }

        if (nonEmptyQueueIndices.size == 1) {
            if (state.taskQueueLength[nonEmptyQueueIndices.first()] == 1) {
                return LocalOnlyPolicy.getActionForState(state)
            }
        }

        for (queueIndexA in nonEmptyQueueIndices.indices) {
            for (queueIndexB in nonEmptyQueueIndices.indices) {
                if (queueIndexA != queueIndexB || state.taskQueueLength[nonEmptyQueueIndices[queueIndexA]] > 1) {
                    return Action.AddToBothUnits(
                        cpuTaskQueueIndex = nonEmptyQueueIndices[queueIndexA],
                        transmissionUnitTaskQueueIndex = nonEmptyQueueIndices[queueIndexB]
                    )
                }
            }
        }

        throw IllegalStateException()
    }
}

object GreedyOffloadFirstPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        if (state.cpuState != 0 && state.tuState != 0) {
            return Action.NoOperation
        }
        if (state.cpuState != 0) {
            return TransmitOnlyPolicy.getActionForState(state)
        }
        if (state.tuState != 0) {
            return LocalOnlyPolicy.getActionForState(state)
        }

        val nonEmptyQueueIndices = state.taskQueueLength.mapIndexed { index, i ->
            if (state.taskQueueLength[index] > 0) index else null
        }.filterNotNull()

        if (nonEmptyQueueIndices.isEmpty()) {
            return Action.NoOperation
        }

        if (nonEmptyQueueIndices.size == 1) {
            if (state.taskQueueLength[nonEmptyQueueIndices.first()] == 1) {
                return TransmitOnlyPolicy.getActionForState(state)
            }
        }

        for (queueIndexA in nonEmptyQueueIndices.indices) {
            for (queueIndexB in nonEmptyQueueIndices.indices) {
                if (queueIndexA != queueIndexB || state.taskQueueLength[nonEmptyQueueIndices[queueIndexA]] > 1) {
                    return Action.AddToBothUnits(
                        cpuTaskQueueIndex = nonEmptyQueueIndices[queueIndexA],
                        transmissionUnitTaskQueueIndex = nonEmptyQueueIndices[queueIndexB]
                    )
                }
            }
        }

        throw IllegalStateException()
    }
}