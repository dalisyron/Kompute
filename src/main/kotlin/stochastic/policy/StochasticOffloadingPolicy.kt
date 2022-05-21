package stochastic.policy

import policy.Action
import core.policy.Policy
import core.policy.UserEquipmentExecutionState
import stochastic.lp.Index
import stochastic.lp.StochasticPolicyConfig
import core.ue.OffloadingSystemConfig
import core.ue.UserEquipmentStateConfig.Companion.allStates
import stochastic.dtmc.DTMCCreator
import java.lang.IllegalStateException
import kotlin.math.abs
import kotlin.random.Random

data class StochasticOffloadingPolicy(
    val stochasticPolicyConfig: StochasticPolicyConfig,
    val systemConfig: OffloadingSystemConfig
) : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        val actionProbabilities: List<Pair<Action, Double>> = systemConfig.allActions.map {
            it to stochasticPolicyConfig.decisionProbabilities[Index(state.ueState, it)]!!
        }

        return getActionFromProbabilityDistribution(actionProbabilities)
    }

    fun validate() {
        systemConfig.stateConfig.allStates().forEach { state ->
            var probabilitySum = 0.0
            systemConfig.allActions.forEach { action ->
                val actionProbability = stochasticPolicyConfig.decisionProbabilities[Index(state, action)]!!
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
                    val prob = stochasticPolicyConfig.decisionProbabilities[Index(state, action)]
                    println("Probability for $action = $prob")
                }
                println("Error End>>")

            }
        }
    }

    override fun toString(): String {
        val lines = mutableListOf<String>()
        lines.add("===============")
        lines.add("POLICY : ")
        lines.add("eta = ${stochasticPolicyConfig.eta}")
        stochasticPolicyConfig.decisionProbabilities.forEach { (key: Index, value: Double) ->
            lines.add("$key : $value")
        }
        lines.add("=============+=")
        return lines.joinToString(separator = "\n")
    }

    companion object {

        fun getActionFromProbabilityDistribution(distribution: List<Pair<Action, Double>>): Action {
            val cumulativeProbabilities = distribution.map { it.second }.scan(0.0) { acc, d -> acc + d }
            // println("Cumulative probabilities = $cumulativeProbabilities")
            val rand = Random.nextDouble()
            check(rand > 0)

            for (i in 0 until cumulativeProbabilities.size - 1) {
                if (rand > cumulativeProbabilities[i] && rand <= cumulativeProbabilities[i + 1]) {
                    return distribution[i].first
                }
            }

            throw IllegalStateException("rand = $rand | cumulative =$cumulativeProbabilities | distribution = $distribution")
        }
    }
}