package simulation.app

import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import stochastic.lp.RangedOptimalPolicyFinder

fun main() {
    val alpha = 0.26
    val systemConfig = Mock.configFromLiyu().withAlpha(alpha)
    val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(systemConfig, 100)

    val simulationTicks = 100_000
    val simulator = Simulator(systemConfig)
    val delay = simulator.simulatePolicy(optimalPolicy, simulationTicks).averageDelay

    println(delay)
}