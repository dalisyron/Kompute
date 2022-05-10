package policy

import ue.UserEquipmentState

object LocalOnlyPolicy : Policy {
    override fun getActionForState(state: UserEquipmentState): Action {
        if (state.cpuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToCPU
        } else {
            return Action.NoOperation
        }
    }
}

object TransmitOnlyPolicy : Policy {
    override fun getActionForState(state: UserEquipmentState): Action {
        if (state.tuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToTransmissionUnit
        } else {
            return Action.NoOperation
        }
    }
}

object GreedyPolicyLocalFirst : Policy {

    override fun getActionForState(state: UserEquipmentState): Action {
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

object GreedyPolicyOffloadFirst : Policy {

    override fun getActionForState(state: UserEquipmentState): Action {
        val canRunLocally = state.cpuState == 0
        val canTransmit = state.tuState == 0

        if (canRunLocally && canTransmit && state.taskQueueLength >= 2) {
            return Action.AddToBothUnits
        } else if (canTransmit && state.taskQueueLength >= 1) {
            return Action.AddToTransmissionUnit
        } else if (canRunLocally && state.taskQueueLength >= 1) {
            return Action.AddToCPU
        } else {
            return Action.NoOperation
        }
    }
}
