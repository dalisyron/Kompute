package ue

import environment.EnvironmentParameters
import policy.Action

data class UserEquipmentConfig(
    val stateConfig: UserEquipmentStateConfig,
    val componentsConfig: UserEquipmentComponentsConfig
) {
    val taskQueueCapacity: Int = stateConfig.taskQueueCapacity
    val tuNumberOfPackets: Int = stateConfig.tuNumberOfPackets
    val cpuNumberOfSections: Int = stateConfig.cpuNumberOfSections
    val beta: Double = componentsConfig.beta
    val alpha: Double = componentsConfig.alpha
    val eta: Double = componentsConfig.eta
    val pTx: Double = componentsConfig.pTx
    val pLoc: Double = componentsConfig.pLoc
    val nLocal: Int = componentsConfig.nLocal
}

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
    val alpha: Double,
    val eta: Double,
    val pTx: Double,
    val pLoc: Double,
    val nLocal: Int
)

data class OffloadingSystemConfig(
    val userEquipmentConfig: UserEquipmentConfig,
    val environmentParameters: EnvironmentParameters,
    val pMax: Double,
    val allActions: Set<Action>
) {
    val actionCount: Int = allActions.size
    val taskQueueCapacity: Int = userEquipmentConfig.stateConfig.taskQueueCapacity
    val tuNumberOfPackets: Int = userEquipmentConfig.stateConfig.tuNumberOfPackets
    val cpuNumberOfSections: Int = userEquipmentConfig.stateConfig.cpuNumberOfSections
    val beta: Double = userEquipmentConfig.componentsConfig.beta
    val alpha: Double = userEquipmentConfig.componentsConfig.alpha
    val eta: Double = userEquipmentConfig.componentsConfig.eta
    val pTx: Double = userEquipmentConfig.componentsConfig.pTx
    val pLoc: Double = userEquipmentConfig.componentsConfig.pLoc
    val nLocal: Int = userEquipmentConfig.componentsConfig.nLocal
    val nCloud: Int = environmentParameters.nCloud
    val tRx: Double = environmentParameters.tRx

    val stateConfig = userEquipmentConfig.stateConfig
}