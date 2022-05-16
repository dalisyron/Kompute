package stochastic.app

import environment.EnvironmentParameters
import policy.Action
import stochastic.lp.OptimalPolicyFinder
import ue.OffloadingSystemConfig
import ue.UserEquipmentComponentsConfig
import ue.UserEquipmentConfig
import ue.UserEquipmentStateConfig

fun main() {
    val environmentParameters = EnvironmentParameters(
        nCloud = 1,
        tRx = 0.0,
    )
    val userEquipmentConfig = UserEquipmentConfig(
        stateConfig = UserEquipmentStateConfig(
            taskQueueCapacity = 10,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        ),
        componentsConfig = UserEquipmentComponentsConfig(
            alpha = 0.4,
            beta = 0.4,
            eta = 0.2,
            pTx = 1.0,
            pLoc = 0.8,
            nLocal = 17
        )
    )
    val systemCofig = OffloadingSystemConfig(
        userEquipmentConfig = userEquipmentConfig,
        environmentParameters = environmentParameters,
        allActions = setOf(
            Action.NoOperation,
            Action.AddToCPU,
            Action.AddToTransmissionUnit,
            Action.AddToBothUnits
        ),
        pMax = 200.0
    )

    val optimalPolicyFinder = OptimalPolicyFinder(systemCofig)

    val policy = optimalPolicyFinder.findOptimalPolicy(100)

    println("Optimal Policy for Given Config: $policy")
}
