package ue

data class UserEquipmentConfig(
    val stateConfig: UserEquipmentStateConfig,
    val componentsConfig: UserEquipmentComponentsConfig
)

data class UserEquipmentStateConfig(
    val taskQueueCapacity: Int,
    val tuNumberOfPackets: Int,
    val cpuNumberOfSections: Int,
) {

    companion object {
        fun UserEquipmentStateConfig.allStates(): List<UserEquipmentState> {
            val states: MutableList<UserEquipmentState> = mutableListOf()

            for (i in 0..taskQueueCapacity) {
                for (j in 0..tuNumberOfPackets) {
                    for (k in 0 until cpuNumberOfSections) {
                        states.add(UserEquipmentState(i, j, k))
                    }
                }
            }
            return states
        }
    }
}

data class UserEquipmentComponentsConfig(
    val beta: Double,
    val alpha: Double
)