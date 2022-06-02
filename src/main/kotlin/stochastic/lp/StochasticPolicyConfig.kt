package stochastic.lp

import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentState

data class StochasticPolicyConfig(
    val etaConfig: List<Double>,
    val averageDelay: Double,
    val decisionProbabilities: Map<StateAction, Double>,
    val stateProbabilities: Map<UserEquipmentState, Double>,
    val stateActionProbabilities: Map<StateAction, Double>,
    val systemConfig: OffloadingSystemConfig
)