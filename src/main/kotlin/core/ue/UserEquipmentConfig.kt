package core.ue

import core.StateManagerConfig
import core.TupleGenerator
import core.environment.EnvironmentParameters
import core.policy.Action
import kotlin.math.pow

data class UserEquipmentConfig(
    val stateConfig: UserEquipmentStateConfig,
    val componentsConfig: UserEquipmentComponentsConfig
) {
    val taskQueueCapacity: Int = stateConfig.taskQueueCapacity
    val tuNumberOfPackets: List<Int> = stateConfig.tuNumberOfPackets
    val cpuNumberOfSections: List<Int> = stateConfig.cpuNumberOfSections
    val beta: Double = componentsConfig.beta
    val alpha: List<Double> = componentsConfig.alpha
    val eta: List<Double> = componentsConfig.eta
    val pTx: Double = componentsConfig.pTx
    val pLoc: Double = componentsConfig.pLocal
    val numberOfQueues: Int = stateConfig.numberOfQueues
}

data class UserEquipmentStateConfig(
    val taskQueueCapacity: Int,
    val tuNumberOfPackets: List<Int>,
    val cpuNumberOfSections: List<Int>,
    val numberOfQueues: Int
) {

    companion object {
        fun UserEquipmentStateConfig.allStates(): List<UserEquipmentState> {
            val dimensions = (1..numberOfQueues).map { taskQueueCapacity + 1 } + (tuNumberOfPackets + 1) + (cpuNumberOfSections) + (numberOfQueues + 1) + (numberOfQueues + 1)
            val states: List<UserEquipmentState> = TupleGenerator.generateTuples(dimensions).map { tuple ->
                val cpuTaskTypeQueueIndex: Int? = tuple[tuple.size - 1].takeIf { it != numberOfQueues}
                val tuTaskTypeQueueIndex: Int? = tuple[tuple.size - 2].takeIf { it != numberOfQueues}

                UserEquipmentState(
                    taskQueueLengths = tuple.subList(0, numberOfQueues),
                    tuState = numberOfQueues,
                    cpuState = numberOfQueues,
                    tuTaskTypeQueueIndex = tuTaskTypeQueueIndex,
                    cpuTaskTypeQueueIndex = cpuTaskTypeQueueIndex
                )
            }

            return states
        }
    }
}

data class UserEquipmentComponentsConfig(
    val beta: Double,
    val alpha: List<Double>,
    val eta: List<Double>,
    val pTx: Double,
    val pLocal: Double,
    val pMax: Double
)

data class OffloadingSystemConfig(
    val userEquipmentConfig: UserEquipmentConfig,
    val environmentParameters: EnvironmentParameters,
    val allActions: Set<Action>
) {
    val taskQueueCapacity: Int = userEquipmentConfig.stateConfig.taskQueueCapacity
    val tuNumberOfPackets: List<Int> = userEquipmentConfig.stateConfig.tuNumberOfPackets
    val cpuNumberOfSections: List<Int> = userEquipmentConfig.stateConfig.cpuNumberOfSections
    val beta: Double = userEquipmentConfig.componentsConfig.beta
    val alpha: List<Double> = userEquipmentConfig.componentsConfig.alpha
    val eta: List<Double> = userEquipmentConfig.componentsConfig.eta
    val pTx: Double = userEquipmentConfig.componentsConfig.pTx
    val pLoc: Double = userEquipmentConfig.componentsConfig.pLocal
    val nCloud: Int = environmentParameters.nCloud
    val tRx: Double = environmentParameters.tRx
    val pMax: Double = userEquipmentConfig.componentsConfig.pMax
    val numberOfQueues: Int = userEquipmentConfig.numberOfQueues

    val tTx: List<Double> by lazy {
        getTx()
    }

    fun getTx(): List<Double> {
        return (0 until userEquipmentConfig.numberOfQueues).map { index ->
            var expectedSingleDelay = 0.0
            val beta = beta
            val numberOfPackets = tuNumberOfPackets[index]
            for (j in 1..1000) { // in theory, infinity is used instead of 1000. But 1000 is precise enough for practice
                expectedSingleDelay += j * (1.0 - beta).pow(j - 1) * beta
            }
            return@map numberOfPackets * expectedSingleDelay
        }
    }

    fun expectedTCloud(queueIndex: Int): Double {
        return tRx + nCloud + tTx[queueIndex]
    }

    fun stateCount(): Int {
        var product = 1
        val dimensions = (1..numberOfQueues).map { taskQueueCapacity + 1 } + (tuNumberOfPackets + 1) + (cpuNumberOfSections) + (numberOfQueues + 1) + (numberOfQueues + 1)

        for (d in dimensions) {
            product *= d
        }
        return product
    }

    val stateConfig = userEquipmentConfig.stateConfig

    fun getLimitation(): List<StateManagerConfig.Limitation> {
        return userEquipmentConfig.eta.map { eta ->
            if (eta == 1.0) {
                StateManagerConfig.Limitation.LocalOnly
            } else if (eta == 0.0) {
                StateManagerConfig.Limitation.OffloadOnly
            } else {
                StateManagerConfig.Limitation.None
            }
        }
    }

    fun getStateManagerConfig(): StateManagerConfig {
        return StateManagerConfig(
            userEquipmentStateConfig = stateConfig,
            limitation = getLimitation()
        )
    }

    companion object {
        fun OffloadingSystemConfig.withAlpha(alpha: List<Double>): OffloadingSystemConfig {
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

        fun OffloadingSystemConfig.withEta(eta: List<Double>): OffloadingSystemConfig {
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

        fun OffloadingSystemConfig.withNumberOfSections(cpuNumberOfSections: List<Int>): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        cpuNumberOfSections = cpuNumberOfSections
                    )
                )
            )
        }


        fun OffloadingSystemConfig.withNumberOfPackets(tuNumberOfPackets: List<Int>): OffloadingSystemConfig {
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