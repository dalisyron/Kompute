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
    val nLocal: Int,
    val pMax: Double
)

data class OffloadingSystemConfig(
    val userEquipmentConfig: UserEquipmentConfig,
    val environmentParameters: EnvironmentParameters,
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
    val pMax: Double = userEquipmentConfig.componentsConfig.pMax

    val stateConfig = userEquipmentConfig.stateConfig

    companion object {
        fun OffloadingSystemConfig.withAlpha(alpha: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        alpha = alpha
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withEta(eta: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        eta = eta
                    )
                )
            )
        }
    }
}