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
    val eta: List<Double>? = componentsConfig.etaConfig
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


        fun singleQueue(
            taskQueueCapacity: Int,
            tuNumberOfPackets: Int,
            cpuNumberOfSections: Int
        ): UserEquipmentStateConfig {
            return UserEquipmentStateConfig(
                taskQueueCapacity = taskQueueCapacity,
                tuNumberOfPackets = listOf(tuNumberOfPackets),
                cpuNumberOfSections = listOf(cpuNumberOfSections),
                numberOfQueues = 1
            )
        }
    }
}

data class UserEquipmentComponentsConfig(
    val beta: Double,
    val alpha: List<Double>,
    val etaConfig: List<Double>?,
    val pTx: Double,
    val pLocal: Double,
    val pMax: Double
) {
    val totalAlpha: Double = alpha.sum()

    companion object {

        fun singleQueue(
            beta: Double,
            alpha: Double,
            etaConfig: Double?,
            pTx: Double,
            pLocal: Double,
            pMax: Double
        ): UserEquipmentComponentsConfig {
            return UserEquipmentComponentsConfig(
                beta = beta,
                alpha = listOf(alpha),
                etaConfig = if (etaConfig == null) null else listOf(etaConfig),
                pTx = pTx,
                pLocal = pLocal,
                pMax = pMax
            )
        }
    }
}

data class OffloadingSystemConfig(
    val userEquipmentConfig: UserEquipmentConfig,
    val environmentParameters: EnvironmentParameters,
) {
    val allActions: Set<Action> by lazy {
        createActionSet()
    }
    val totalAlpha = userEquipmentConfig.componentsConfig.totalAlpha

    private fun createActionSet(): Set<Action> {
        val result: MutableSet<Action> = mutableSetOf()

        result.add(Action.NoOperation)

        for (queueIndex in 0 until userEquipmentConfig.numberOfQueues) {
            result.add(Action.AddToCPU(queueIndex))
            result.add(Action.AddToTransmissionUnit(queueIndex))
        }

        for (queueIndexA in 0 until userEquipmentConfig.numberOfQueues) {
            for (queueIndexB in 0 until userEquipmentConfig.numberOfQueues) {
                result.add(
                    Action.AddToBothUnits(
                        transmissionUnitTaskQueueIndex = queueIndexA,
                        cpuTaskQueueIndex = queueIndexB
                    )
                )
            }
        }

        return result
    }

    val taskQueueCapacity: Int = userEquipmentConfig.stateConfig.taskQueueCapacity
    val tuNumberOfPackets: List<Int> = userEquipmentConfig.stateConfig.tuNumberOfPackets
    val cpuNumberOfSections: List<Int> = userEquipmentConfig.stateConfig.cpuNumberOfSections
    val beta: Double = userEquipmentConfig.componentsConfig.beta
    val alpha: List<Double> = userEquipmentConfig.componentsConfig.alpha
    val eta: List<Double>? = userEquipmentConfig.componentsConfig.etaConfig
    val pTx: Double = userEquipmentConfig.componentsConfig.pTx
    val pLoc: Double = userEquipmentConfig.componentsConfig.pLocal
    val nCloud: List<Int> = environmentParameters.nCloud
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
            for (j in 1..100) { // in theory, infinity is used instead of 100. But 100 is precise enough for practice
                expectedSingleDelay += j * (1.0 - beta).pow(j - 1) * beta
            }
            return@map numberOfPackets * expectedSingleDelay
        }
    }

    fun expectedTCloud(queueIndex: Int): Double {
        return tRx + nCloud[queueIndex] + tTx[queueIndex]
    }

    fun expectedTaskTime(queueIndex: Int): Double {
        val eta: Double = eta!![queueIndex]
        val numberOfSections: Int = cpuNumberOfSections[queueIndex]
        return (eta * numberOfSections + (1 - eta) * expectedTCloud(queueIndex))
    }

    fun stateCount(): Int {
        var product = 1
        val dimensions =
            (1..numberOfQueues).map { taskQueueCapacity + 1 } + (tuNumberOfPackets + 1) + (cpuNumberOfSections) + (numberOfQueues + 1) + (numberOfQueues + 1)

        for (d in dimensions) {
            product *= d
        }
        return product
    }

    val stateConfig = userEquipmentConfig.stateConfig

    fun getLimitation(): List<StateManagerConfig.Limitation> {
        if (userEquipmentConfig.eta == null) {
            return (1..numberOfQueues).map { StateManagerConfig.Limitation.None }
        }
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

        fun OffloadingSystemConfig.withAlphaSingleQueue(alpha: Double): OffloadingSystemConfig {
            require(numberOfQueues == 1)
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        alpha = listOf(alpha)
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withEtaConfigSingleQueue(eta: Double): OffloadingSystemConfig {
            require(numberOfQueues == 1)
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        etaConfig = listOf(eta)
                    )
                )
            )
        }

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

        fun OffloadingSystemConfig.withEtaConfig(etaConfig: List<Double>): OffloadingSystemConfig {
            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    componentsConfig = userEquipmentConfig.componentsConfig.copy(
                        etaConfig = etaConfig
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

        fun OffloadingSystemConfig.withNumberOfSectionsSingleQueue(cpuNumberOfSections: Int): OffloadingSystemConfig {
            require(numberOfQueues == 1)

            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        cpuNumberOfSections = listOf(cpuNumberOfSections)
                    )
                )
            )
        }

        fun OffloadingSystemConfig.withNumberOfPacketsSingleQueue(tuNumberOfPackets: Int): OffloadingSystemConfig {
            require(numberOfQueues == 1)

            return this.copy(
                userEquipmentConfig = userEquipmentConfig.copy(
                    stateConfig = userEquipmentConfig.stateConfig.copy(
                        tuNumberOfPackets = listOf(tuNumberOfPackets)
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