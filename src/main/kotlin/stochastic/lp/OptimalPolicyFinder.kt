package stochastic.lp

import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.UserEquipmentStateConfig.Companion.allStates
import ue.UserEquipmentState

object RangedOptimalPolicyFinder {

    fun findOptimalPolicy(baseSystemConfig: OffloadingSystemConfig, precision: Int): StochasticOffloadingPolicy {

        var optimalPolicy: StochasticOffloadingPolicy? = null

        for (i in 0..precision) {
            val eta = i.toDouble() / precision
            println("cycle $i of $precision | eta = $eta")
            val etaConfig = baseSystemConfig.withEta(eta)
            val optimalPolicyWithGivenEta = OptimalPolicyFinder.findOptimalPolicy(etaConfig)

            if (optimalPolicy == null || optimalPolicyWithGivenEta.averageDelay < optimalPolicy.averageDelay) {
                optimalPolicy = optimalPolicyWithGivenEta
            }
        }

        return optimalPolicy!!
    }
}

object OptimalPolicyFinder {

    fun findOptimalPolicy(systemConfig: OffloadingSystemConfig): StochasticOffloadingPolicy {
        val optimalConfig = OffloadingSolver(systemConfig).findOptimalStochasticConfig()

        return StochasticOffloadingPolicy(
            systemConfig = systemConfig,
            stochasticPolicyConfig = optimalConfig
        )
    }
    class EffectivePolicyNotExistsException : Exception()
}

class OffloadingSolver(
    private val systemConfig: OffloadingSystemConfig
) {

    fun findOptimalStochasticConfig(): StochasticPolicyConfig {
        val offloadingLPCreator: OffloadingLPCreator = OffloadingLPCreator(systemConfig)
        val linearProgram = offloadingLPCreator.createStandardLinearProgram()

        val solution: LPSolution = LPSolver.solve(linearProgram)
        val config = solutionToPolicyConfig(solution)

        return config
    }

    private fun solutionToPolicyConfig(optimalSolution: LPSolution): StochasticPolicyConfig {
        val variableCount =
            (systemConfig.taskQueueCapacity + 1) * (systemConfig.tuNumberOfPackets + 1) * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        check(variableCount == optimalSolution.variableValues.size)

        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()

        val stateActionProbabilities = optimalSolution.variableValues.mapIndexed { index, d ->
            stateActionByIndex(index) to d
        }.toMap()

        stateActionProbabilities.forEach { (key: StateAction, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val decisions: Map<StateAction, Double> =
            stateActionProbabilities.mapValues { (key: StateAction, value: Double) ->
                if (stateProbabilities[key.state] == 0.0) {
                    0.0
                } else {
                    value / stateProbabilities[key.state]!!
                }
            }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            eta = systemConfig.eta,
            averageDelay = optimalSolution.objectiveValue,
            systemConfig = systemConfig,
            stateProbabilities = stateProbabilities
        )
    }

    private fun stateActionByIndex(index: Int): StateAction {
        val r1 = (systemConfig.tuNumberOfPackets + 1) * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        val taskQueueLength = index / r1
        val r2 = (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        val tuState = (index % r1) / r2
        val r3 = systemConfig.actionCount
        val cpuState = ((index % r1) % r2) / systemConfig.actionCount
        val action = systemConfig.allActions.find { it.order == ((index % r1) % r2) % r3 }!!

        return StateAction(
            state = UserEquipmentState(
                taskQueueLength = taskQueueLength,
                tuState = tuState,
                cpuState = cpuState
            ),
            action = action
        )
    }

    data class StochasticPolicyConfig(
        val eta: Double,
        val averageDelay: Double,
        val decisionProbabilities: Map<StateAction, Double>,
        val stateProbabilities: Map<UserEquipmentState, Double>,
        val systemConfig: OffloadingSystemConfig
    ) {

        fun fullQueueProbability(): Double {
            val probabilityQueueFull: Double = systemConfig.stateConfig.allStates()
                .filter { it.taskQueueLength == systemConfig.taskQueueCapacity }
                .sumOf { state -> stateProbabilities[state]!! }
            return probabilityQueueFull
        }
    }
}

interface IndexToStateActionMapper {

    fun mapToStateAction(index: Int, systemConfig: OffloadingSystemConfig): StateAction
}