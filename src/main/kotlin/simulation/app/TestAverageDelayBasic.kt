package simulation.app

import com.github.sh0nk.matplotlib4j.Plot
import core.policy.*
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.OffloadingSystemConfig.Companion.withNumberOfPackets
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withPLocal
import core.ue.OffloadingSystemConfig.Companion.withPMax
import core.ue.OffloadingSystemConfig.Companion.withPTx
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import stochastic.lp.RangedOptimalPolicyFinder

fun main() {
    val alphas: List<Double> = (30..44).map { (it * 0.5) / 100.0 }

    val baseConfig = Mock.configFromLiyu()
        .withPMax(2.5)
        .withTaskQueueCapacity(15)
        .withNumberOfSections(9)
        .withPLocal(4.0/3.0)
        .withBeta(0.99)
        .withNumberOfPackets(7) // score tx = (1) / (4.0 * 3.0)
        .withPTx(2.0)

    val stochasticPolicies: List<StochasticOffloadingPolicy> = alphas.map { alpha ->
        println("Calculating for alpha = $alpha")
        val systemConfig = baseConfig.withAlpha(alpha)
        RangedOptimalPolicyFinder.findOptimalPolicy(systemConfig, 0.0, 0.99, 75)
    }

    var lastPercent = 0.0
    val simulationTicks = 1_000_000

    val localOnlyDelays: MutableList<Double> = mutableListOf()
    val transmitOnlyDelays: MutableList<Double> = mutableListOf()
    val greedyOffloadFirstDelays: MutableList<Double> = mutableListOf()
    val greedyLocalFirstDelays: MutableList<Double> = mutableListOf()
    val stochasticDelays: MutableList<Double> = mutableListOf()

    alphas.forEachIndexed { i, alpha ->
        val systemConfig = baseConfig.withAlpha(alpha)
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
        println("simulating ${i + 1} from ${alphas.size}")
    }

    val plot = Plot.create()
    println("localOnly = $localOnlyDelays")
    stochasticPolicies.forEach {
        println(it)
    }
    plot.plot().add(alphas, localOnlyDelays).color("tab:olive").label("Local Only")
    plot.plot().add(alphas, transmitOnlyDelays).color("blue").label("Offload Only")
    plot.plot().add(alphas, greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
    plot.plot().add(alphas, greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
    plot.plot().add(alphas, stochasticDelays).color("red").label("Optimal Stochastic")

    plot.xlabel("The average arrival rate (alpha)")
    plot.ylabel("The average delay")
    plot.title("Average delay for policies")
    plot.ylim(0, 20)
    plot.xlim(0, 0.4)
    plot.legend()
    plot.show()
}
