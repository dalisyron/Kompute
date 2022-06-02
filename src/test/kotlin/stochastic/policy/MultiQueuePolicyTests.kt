package stochastic.policy

import core.policy.GreedyOffloadFirstPolicy
import core.policy.LocalOnlyPolicy
import core.policy.TransmitOnlyPolicy
import org.junit.Test
import simulation.app.Mock
import simulation.simulation.Simulator
import stochastic.lp.RangedOptimalPolicyFinder

class MultiQueuePolicyTests {

    @Test
    fun compareSimulationWithEstimate() {
        val stochasticPolicy: StochasticOffloadingPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(
            Mock.doubleQueueConfig1(),
            20
        )
        val averageDelayEstimate = stochasticPolicy.averageDelay

        val simulator = Simulator(stochasticPolicy.systemConfig)

        val averageDelayActual = simulator.simulatePolicy(stochasticPolicy, 2_000_000).averageDelay
        val delayOffloadOnly = simulator.simulatePolicy(TransmitOnlyPolicy, 2_000_000).averageDelay
        val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, 2_000_000).averageDelay
        val greedyOffloadFirstDelay = simulator.simulatePolicy(GreedyOffloadFirstPolicy, 2_000_000).averageDelay

        println("$averageDelayActual | $averageDelayEstimate | $delayOffloadOnly | $localOnlyDelay | $greedyOffloadFirstDelay")
    }
}