package ue

data class UserEquipmentState(
    val taskQueueLength: Int,
    val tuState: Int,
    val cpuState: Int
) {

    companion object {
        fun UserEquipmentState.validate() {
            check(taskQueueLength >= 0)
            check(tuState >= 0)
            check(cpuState >= -1)
        }
    }

    override fun toString(): String {
        return "($taskQueueLength, $tuState, $cpuState)"
    }
}