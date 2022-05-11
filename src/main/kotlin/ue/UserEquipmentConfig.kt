package ue

data class UserEquipmentConfig(
    val stateConfig: UserEquipmentStateConfig,
    val componentsConfig: UserEquipmentComponentsConfig
)

data class UserEquipmentStateConfig(
    val taskQueueCapacity: Int,
    val tuNumberOfPackets: Int,
    val cpuNumberOfSections: Int,
)

data class UserEquipmentComponentsConfig(
    val beta: Double,
    val alpha: Double
)