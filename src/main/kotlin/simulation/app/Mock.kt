package simulation.app

import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig

object Mock {

    fun configFromLiyu(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 20, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.4,
                beta = 0.4,
                etaConfig = null, // Not used in the baseline policies, set to whatever
                pTx = 1.0,
                pLocal = 0.8,
                pMax = 20.0
            )
        )

        val systemConfig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
        )
        return systemConfig
    }

    fun simpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 2,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 2
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.30,
                beta = 0.4,
                etaConfig = 0.2,
                pTx = 1.0,
                pLocal = 0.8,
                pMax = 200.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
        )

        return systemCofig
    }


    fun doubleQueueConfig1(): OffloadingSystemConfig {
        return OffloadingSystemConfig(
            userEquipmentConfig = UserEquipmentConfig(
                stateConfig = UserEquipmentStateConfig(
                    taskQueueCapacity = 6,
                    tuNumberOfPackets = listOf(1, 2),
                    cpuNumberOfSections = listOf(4, 3),
                    numberOfQueues = 2
                ),
                componentsConfig = UserEquipmentComponentsConfig(
                    alpha = listOf(0.1, 0.2),
                    beta = 0.9,
                    etaConfig = null,
                    pTx = 1.0,
                    pLocal = 0.8,
                    pMax = 200.0
                )
            ),
            environmentParameters = EnvironmentParameters(
                nCloud = 1,
                tRx = 0.0,
            )
        )
    }
}