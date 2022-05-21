package stochastic.lp

import stochastic.dtmc.DTMCCreator
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig
import ue.UserEquipmentState
import core.ue.UserEquipmentStateConfig.Companion.allStates
import kotlin.math.abs

data class StochasticPolicyConfig(
    val eta: Double,
    val averageDelay: Double,
    val decisionProbabilities: Map<Index, Double>
)

class OptimalPolicyFinder(
    private val systemConfig: OffloadingSystemConfig
) {

    fun findOptimalPolicy(precision: Int): StochasticOffloadingPolicy {
        val optimalSolution = findOptimalSolution(precision)
        val stochasticPolicyConfig = solutionToPolicyConfig(optimalSolution)
        val policy = StochasticOffloadingPolicy(
            stochasticPolicyConfig = stochasticPolicyConfig,
            systemConfig = systemConfig
        )
        return policy
    }


    data class LPOffloadingSolution(
        val eta: Double,
        val averageDelay: Double,
        val decisionProbabilities: List<Double>
    )

    private fun findOptimalSolution(precision: Int): LPOffloadingSolution {
        var optimalSolution: LPSolution? = null
        var minEta: Double = 0.0

        for (i in 0..precision) {
            println("cycle $i of $precision")
            val eta = i.toDouble() / precision

            val tempConfig = systemConfig.copy(
                userEquipmentConfig = systemConfig.userEquipmentConfig.copy(
                    componentsConfig = systemConfig.userEquipmentConfig.componentsConfig.copy(
                        eta = eta
                    )
                )
            )

            val offloadingLPCreator: OffloadingLPCreator = OffloadingLPCreator(tempConfig)
            val linearProgram = offloadingLPCreator.createLP()

            val solution = LPSolver.solve(linearProgram)

            if (optimalSolution == null || solution.objectiveValue < optimalSolution.objectiveValue) {
                optimalSolution = solution
                minEta = eta
            }
        }

        return LPOffloadingSolution(
            eta = minEta,
            averageDelay = optimalSolution!!.objectiveValue,
            decisionProbabilities = optimalSolution.variableValues
        )
    }

    fun findOptimalWithGivenEta(eta: Double): StochasticPolicyConfig {
        val tempConfig = systemConfig.copy(
            userEquipmentConfig = systemConfig.userEquipmentConfig.copy(
                componentsConfig = systemConfig.userEquipmentConfig.componentsConfig.copy(
                    eta = eta
                )
            )
        )

        val offloadingLPCreator: OffloadingLPCreator = OffloadingLPCreator(tempConfig)
        val linearProgram = offloadingLPCreator.createLP()

        val solution = LPSolver.solve(linearProgram)
        println("solution = $solution")

        val offloadingSolution = LPOffloadingSolution(
            eta = eta,
            averageDelay = solution.objectiveValue,
            decisionProbabilities = solution.variableValues
        )
        return solutionToPolicyConfig(offloadingSolution)
    }
}

class OffloadingLinearProgrammingSolver(
    private val systemConfig: OffloadingSystemConfig
) {

    fun solutionToPolicyConfig(optimalSolution: OptimalPolicyFinder.LPOffloadingSolution): StochasticPolicyConfig {
        val variableCount =
            (systemConfig.taskQueueCapacity + 1) * (systemConfig.tuNumberOfPackets + 1) * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        check(variableCount == optimalSolution.decisionProbabilities.size)

        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()

        val stateActionProbabilities =
            optimalSolution.decisionProbabilities.mapIndexed { index, d -> stateActionIndex(index) to d }.toMap()

        stateActionProbabilities.forEach { (key: Index, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val decisions: Map<Index, Double> = stateActionProbabilities.mapValues { (key: Index, value: Double) ->
            value / stateProbabilities[key.state]!!
        }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            eta = optimalSolution.eta,
            averageDelay = optimalSolution.averageDelay
        )
    }
}