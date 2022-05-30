package core.ue

data class UserEquipmentState(
    val taskQueueLengths: List<Int>,
    val tuState: Int,
    val cpuState: Int,
    val tuTaskTypeQueueIndex: Int?,
    val cpuTaskTypeQueueIndex: Int?
) {

    override fun toString(): String {
        return "{ QueueLengths = (${taskQueueLengths.joinToString(", ")}), TU State = ($tuState), TU TaskTypeQueueIndex = ($tuTaskTypeQueueIndex), CPUState = (${cpuState}), CPUTaskTypeQueueIndex = (${cpuTaskTypeQueueIndex})"
    }

    fun isCPUActive(): Boolean {
        return cpuState > 0 || cpuState == -1
    }

    fun isTUActive(): Boolean {
        return tuState > 0
    }
}