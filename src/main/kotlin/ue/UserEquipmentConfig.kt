package ue

data class UserEquipmentConfig(
    val taskQueueCapacity: Int,
    val tuNumberOfPackets: Int,
    val cpuNumberOfSections: Int,
    val beta: Double,
    val alpha: Double
)