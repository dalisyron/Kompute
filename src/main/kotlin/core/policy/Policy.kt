package core.policy

import ue.UserEquipmentState

data class UserEquipmentExecutionState(
    val ueState: UserEquipmentState,
    val timeSlot: Int,
    val totalConsumedPower: Double,
    val pMax: Double
) {
    fun averagePower(): Double = totalConsumedPower / timeSlot
    val taskQueueLength = ueState.taskQueueLength
    val tuState = ueState.tuState
    val cpuState = ueState.cpuState
}

interface Policy {

    fun getActionForState(state: UserEquipmentExecutionState): Action
}