package simulation.app

import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig

object Mock {

    fun configFromLiyu(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters.singleQueue(
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
        val environmentParameters = EnvironmentParameters.singleQueue(
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
                    tuNumberOfPackets = listOf(1, 3),
                    cpuNumberOfSections = listOf(8, 2),
                    numberOfQueues = 2
                ),
                componentsConfig = UserEquipmentComponentsConfig(
                    alpha = listOf(0.4, 0.9),
                    beta = 0.95,
                    etaConfig = null,
                    pTx = 1.0,
                    pLocal = 0.8,
                    pMax = 200.0
                )
            ),
            environmentParameters = EnvironmentParameters(
                nCloud = listOf(1, 1),
                tRx = 0.5,
            )
        )
    }
}