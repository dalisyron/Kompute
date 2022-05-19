package simulation.app

import environment.EnvironmentParameters
import policy.Action
import ue.OffloadingSystemConfig
import ue.UserEquipmentComponentsConfig
import ue.UserEquipmentConfig
import ue.UserEquipmentStateConfig

object Mock {

    fun configFromLiyu(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 12, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.4,
                beta = 0.4,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLoc = 1.5,
                nLocal = 17,
                pMax = 1000.1
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