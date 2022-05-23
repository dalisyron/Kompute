package simulation.app

import com.github.sh0nk.matplotlib4j.Plot
import core.policy.*
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withEta
import stochastic.lp.RangedOptimalPolicyFinder

fun main() {
    val alphas: List<Double> = (1..50).map { (it * 1.0) / 100.0 }

    val stochasticPolicies: List<StochasticOffloadingPolicy> = alphas.map { alpha ->
        println("Calculating for alpha = $alpha")
        val systemConfig = Mock.configFromLiyu().withAlpha(alpha)
        RangedOptimalPolicyFinder.findOptimalPolicy(systemConfig, 0.0, 1.0, 100)
    }

    var lastPercent = 0.0
    val simulationTicks = 4000_000

    val localOnlyDelays: MutableList<Double> = mutableListOf()
    val transmitOnlyDelays: MutableList<Double> = mutableListOf()
    val greedyOffloadFirstDelays: MutableList<Double> = mutableListOf()
    val greedyLocalFirstDelays: MutableList<Double> = mutableListOf()
    val stochasticDelays: MutableList<Double> = mutableListOf()

    alphas.forEachIndexed { i, alpha ->
        val systemConfig = Mock.configFromLiyu().withAlpha(alpha)
        val simulator = Simulator(systemConfig)
        val testPolicies = listOf(
            LocalOnlyPolicy,
            TransmitOnlyPolicy,
            GreedyOffloadFirstPolicy,
            GreedyLocalFirstPolicy,
            stochasticPolicies[i]
        )
        val delays = testPolicies.map {
            simulator.simulatePolicy(it, simulationTicks).averageDelay
        }

        localOnlyDelays.add(delays[0])
        transmitOnlyDelays.add(delays[1])
        greedyOffloadFirstDelays.add(delays[2])
        greedyLocalFirstDelays.add(delays[3])
        stochasticDelays.add(delays[4])
        println("$i from ${alphas.size}")
    }

    val plot = Plot.create()
    println("localOnly = $localOnlyDelays")
    stochasticPolicies.forEach {
        println(it)
    }
    plot.plot().add(alphas, localOnlyDelays).color("red").label("Local Only")
    plot.plot().add(alphas, transmitOnlyDelays).color("blue").label("Offload Only")
    plot.plot().add(alphas, greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
    plot.plot().add(alphas, greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
    plot.plot().add(alphas, stochasticDelays).color("black").label("Optimal Stochastic")

    plot.xlabel("The average arrival rate (alpha)")
    plot.ylabel("The average delay")
    plot.title("Average delay for policies")
    plot.ylim(0, 60)
    plot.xlim(0, 1.0)
    plot.legend()
    plot.show()
}
