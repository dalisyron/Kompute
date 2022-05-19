package stochastic.policy

import policy.Action
import core.policy.Policy
import core.policy.UserEquipmentExecutionState
import stochastic.lp.Index
import stochastic.lp.StochasticPolicyConfig
import ue.OffloadingSystemConfig
import java.lang.IllegalStateException
import kotlin.random.Random

class StochasticOffloadingPolicy(
    val stochasticPolicyConfig: StochasticPolicyConfig,
    val systemConfig: OffloadingSystemConfig
) : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        val actions = systemConfig.allActions
        val actionProbabilities: List<Pair<Action, Double>> = systemConfig.allActions.map {
            it to stochasticPolicyConfig.decisionProbabilities[Index(state.ueState, it)]!!
        }

        return getActionFromProbabilityDistribution(actionProbabilities)
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