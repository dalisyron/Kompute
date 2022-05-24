package simulation.app

import core.environment.EnvironmentParameters
import policy.Action
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig

object Mock {

    fun configFromLiyu(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 13, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 14
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.4,
                beta = 0.5,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.4,
                pLocal = 0.3,
                pMax = 1.6
            )
        )

        val systemConfig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters,
            allActions = setOf(
                Action.NoOperation,
                Action.AddToCPU,
                Action.AddToTransmissionUnit,
                Action.AddToBothUnits
            )
        )
        return systemConfig
    }
}