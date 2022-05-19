package stochastic.lp

import stochastic.dtmc.DTMCCreator
import stochastic.policy.StochasticOffloadingPolicy
import ue.OffloadingSystemConfig
import ue.UserEquipmentState
import ue.UserEquipmentStateConfig.Companion.allStates
import kotlin.math.abs

data class StochasticPolicyConfig(
    val eta: Double,
    val averageDelay: Double,
    val decisionProbabilities: Map<Index, Double>
)

class OptimalPolicyFinder(
    val systemConfig: OffloadingSystemConfig
) {

    fun findOptimalPolicy(precision: Int): StochasticOffloadingPolicy {
        val optimalSolution = findOptimalSolution(precision)
        val stochasticPolicyConfig = solutionToPolicyConfig(optimalSolution)
        val policy = StochasticOffloadingPolicy(
            stochasticPolicyConfig = stochasticPolicyConfig,
            systemConfig = systemConfig
        )
        checkPolicy(policy)
        return policy
    }

    fun checkPolicy(policy: StochasticOffloadingPolicy) {
        systemConfig.stateConfig.allStates().forEach { state ->
            var probabilitySum = 0.0
            systemConfig.allActions.forEach { action ->
                val actionProbability = policy.stochasticPolicyConfig.decisionProbabilities[Index(state, action)]!!
                if (!DTMCCreator.getPossibleActions(state).contains(action)) {
                    check(actionProbability < 1e-9) {
                        println("Policy violates possible actions condition: state = $state | action = $action | probability = $actionProbability")
                    }
                }
                probabilitySum += actionProbability
            }
            check(abs(probabilitySum - 1.0) < 1e-6) {
                println("<<Error Start:")
                println("Policy has invalid probability distribution: state = $state | probabilitySum = $probabilitySum")
                systemConfig.allActions.forEach { action ->
                    val prob = policy.stochasticPolicyConfig.decisionProbabilities[Index(state, action)]
                    println("Probability for $action = $prob")
                }
                println("Error End>>")

            }
        }
    }

    fun solutionToPolicyConfig(optimalSolution: LPOffloadingSolution): StochasticPolicyConfig {
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
            val possibleActions = DTMCCreator.getPossibleActions(key.state)
            if (stateProbabilities[key.state]!! < 1e-6) {
                if (key.action in possibleActions) {
                    1.0 / possibleActions.size
                } else {
                    0.0
                }
            } else {
                value / stateProbabilities[key.state]!!
            }
        }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            eta = optimalSolution.eta,
            averageDelay = optimalSolution.averageDelay
        )
    }
    private fun stateActionIndex(index: Int): Index {
        val r1 = (systemConfig.tuNumberOfPackets + 1) * (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        val taskQueueLength = index / r1
        val r2 = (systemConfig.cpuNumberOfSections) * systemConfig.actionCount
        val tuState = (index % r1) / r2
        val r3 = systemConfig.actionCount
        val cpuState = ((index % r1) % r2) / systemConfig.actionCount
        val action = systemConfig.allActions.find { it.order == ((index % r1) % r2) % r3 }!!

        return Index(
            state = UserEquipmentState(
                taskQueueLength = taskQueueLength,
                tuState = tuState,
                cpuState = cpuState
            ),
            action = action
        )
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
            decisionProbabilities =  optimalSolution.variableValues
        )
    }

    fun getOptimalWithEta(eta: Double): StochasticPolicyConfig {
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

        val offloadingSolution = LPOffloadingSolution(
            eta = eta,
            averageDelay = solution.objectiveValue,
            decisionProbabilities =  solution.variableValues
        )

        return solutionToPolicyConfig(offloadingSolution)
    }
}