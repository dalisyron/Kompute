package core.ue

import core.StateManagerConfig
import core.environment.EnvironmentParameters
import core.policy.Action
import ue.UserEquipmentState
import kotlin.math.pow

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
    val pLoc: Double = componentsConfig.pLocal
}

data class UserEquipmentStateConfig(
    val taskQueueCapacity: Int,
    val tuNumberOfPackets: Int,
    val cpuNumberOfSections: Int,
) {

    fun getFullStates(): List<UserEquipmentState> {
        val states = mutableListOf<UserEquipmentState>()

        for (j in 0..tuNumberOfPackets) {
            for (k in 0 until cpuNumberOfSections) {
                states.add(UserEquipmentState(taskQueueCapacity, j, k))
            }
        }

        return states
    }

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
    val pLocal: Double,
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
    val pLoc: Double = userEquipmentConfig.componentsConfig.pLocal
    val nCloud: Int = environmentParameters.nCloud
    val tRx: Double = environmentParameters.tRx
    val pMax: Double = userEquipmentConfig.componentsConfig.pMax
    val tTx: Double by lazy {
        var expectedSingleDelay = 0.0
        val beta = beta
        val numberOfPackets = tuNumberOfPackets
        for (j in 1..1000) { // in theory, infinity is used instead of 1000. But 1000 is precise enough for practice
            expectedSingleDelay += j * (1.0 - beta).pow(j - 1) * beta
        }
        return@lazy numberOfPackets * expectedSingleDelay
    }

    fun expectedTCloud(): Double {
        return tRx + nCloud + tTx
    }

    fun stateCount(): Int {
        return (taskQueueCapacity + 1) * (tuNumberOfPackets + 1) * (cpuNumberOfSections)
    }

    val stateConfig = userEquipmentConfig.stateConfig

    fun getLimitation(): StateManagerConfig.Limitation {
        if (eta == 1.0) {
            return StateManagerConfig.Limitation.LocalOnly
        } else if (eta == 0.0) {
            return StateManagerConfig.Limitation.OffloadOnly
        } else {
            return StateManagerConfig.Limitation.None
        }
    }

    fun getStateManagerConfig(): StateManagerConfig {
        return StateManagerConfig(
            userEquipmentStateConfig = stateConfig,
            limitation = getLimitation()
        )
    }

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

        fun OffloadingSystemConfig.withBeta(beta: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        beta = beta
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

        fun OffloadingSystemConfig.withTaskQueueCapacity(capacity: Int): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        taskQueueCapacity = capacity
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withNumberOfSections(cpuNumberOfSections: Int): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        cpuNumberOfSections = cpuNumberOfSections
                    )
                )
            )
        }


        fun OffloadingSystemConfig.withNumberOfPackets(tuNumberOfPackets: Int): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        tuNumberOfPackets = tuNumberOfPackets
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withPMax(pMax: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        pMax = pMax
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withUserEquipmentStateConfig(userEquipmentStateConfig: UserEquipmentStateConfig): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentStateConfig
                )
            )
        }

        fun OffloadingSystemConfig.withPLocal(pLocal: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        pLocal = pLocal
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withPTx(pTx: Double): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        pTx = pTx
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withTRx(tRx: Double): OffloadingSystemConfig {
            return this.copy(
                environmentParameters = environmentParameters.copy(
                    tRx = tRx
                )
            )
        }

    }
}