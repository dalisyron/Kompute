package simulation.app

import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import ue.OffloadingSystemConfig.Companion.withAlpha

fun main() {
    val alpha = 0.26
    val systemConfig = Mock.configFromLiyu().withAlpha(alpha)
    val optimalPolicy = OptimalPolicyFinder(systemConfig).findOptimalPolicy(100)

    val simulationTicks = 100_000
    val simulator = Simulator(systemConfig)
    val delay = simulator.simulatePolicy(optimalPolicy, simulationTicks).averageDelay

    println(delay)
}