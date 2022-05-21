package stochastic.app

import core.environment.EnvironmentParameters
import policy.Action
import stochastic.lp.Index
import stochastic.lp.OptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import kotlin.math.abs

fun main() {
    val environmentParameters = EnvironmentParameters(
        nCloud = 1,
        tRx = 0,
    )
    val userEquipmentConfig = UserEquipmentConfig(
        stateConfig = UserEquipmentStateConfig(
            taskQueueCapacity = 30,
            tuNumberOfPackets = 1,
            cpuNumberOfSections = 17
        ),
        componentsConfig = UserEquipmentComponentsConfig(
            alpha = 0.4,
            beta = 0.4,
            eta = 0.2,
            pTx = 1.0,
            pLoc = 0.8,
            pMax = 2.0
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
        )
    )

    val systemConfig2 = systemCofig.copy(
        userEquipmentConfig = systemCofig.userEquipmentConfig.copy(
            componentsConfig = systemCofig.userEquipmentConfig.componentsConfig.copy(
                pMax = 1.0
            )
        )
    )

    val optimalPolicyFinder = OptimalPolicyFinder(systemCofig)

    val policyW2: StochasticOffloadingPolicy = optimalPolicyFinder.findOptimalPolicy(30)
    val policyW1 = OptimalPolicyFinder(systemConfig2).findOptimalPolicy(30)

    policyW1.stochasticPolicyConfig.decisionProbabilities.forEach { (index: Index, probability: Double) ->
        val pW2 = policyW2.stochasticPolicyConfig.decisionProbabilities[index]!!
        if (abs(pW2 - probability) > 1e-4)  {
            println("Mismatch: State = ${index.state} | Action = ${index.action} | 1W Decision: $probability | 2W Decision: $pW2")
        }
    }

    println("W1 : $policyW1")
    println("W2 : $policyW2")
}
