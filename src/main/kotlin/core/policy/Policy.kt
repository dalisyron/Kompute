package core.policy

import policy.Action
import ue.UserEquipmentState

data class UserEquipmentExecutionState(
    val ueState: UserEquipmentState,
    val timeSlot: Int,
    val totalConsumedPower: Double
) {
    fun averagePower(): Double = totalConsumedPower / timeSlot
    val taskQueueLength = ueState.taskQueueLength
    val tuState = ueState.tuState
    val cpuState = ueState.cpuState
}

interface Policy {

    fun getActionForState(state: UserEquipmentExecutionState): Action
}