package core.policy

import policy.Action

object LocalOnlyPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        if (state.cpuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToCPU
        } else {
            return Action.NoOperation
        }
    }
}

object TransmitOnlyPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        if (state.tuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToTransmissionUnit
        } else {
            return Action.NoOperation
        }
    }
}

object GreedyLocalFirstPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        val canRunLocally = state.cpuState == 0
        val canTransmit = state.tuState == 0

        if (canRunLocally && canTransmit && state.taskQueueLength >= 2) {
            return Action.AddToBothUnits
        } else if (canRunLocally && state.taskQueueLength >= 1) {
            return Action.AddToCPU
        } else if (canTransmit && state.taskQueueLength >= 1) {
            return Action.AddToTransmissionUnit
        } else {
            return Action.NoOperation
        }
    }
}

object GreedyOffloadFirstPolicy : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.averagePower() > state.pMax) {
            return Action.NoOperation
        }
        val canRunLocally = state.cpuState == 0
        val canTransmit = state.tuState == 0

        if (canRunLocally && canTransmit && state.taskQueueLength >= 2) {
            return Action.AddToBothUnits
        } else if (canTransmit && state.taskQueueLength >= 1) {
            return Action.AddToTransmissionUnit
        } else if (canRunLocally && state.taskQueueLength >= 1) {
            return Action.AddToCPU
        }

        return Action.NoOperation
    }
}