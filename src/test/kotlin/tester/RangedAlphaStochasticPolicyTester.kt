package tester

import com.github.sh0nk.matplotlib4j.Plot
import com.google.common.truth.Truth
import core.policy.GreedyLocalFirstPolicy
import core.policy.GreedyOffloadFirstPolicy
import core.policy.LocalOnlyPolicy
import core.policy.OffloadOnlyPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlphaSingleQueue
import core.writeToFile
import simulation.simulation.Simulator
import stochastic.lp.ConcurrentRangedOptimalPolicyFinder
import stochastic.lp.RangedOptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy

class RangedAlphaStochasticPolicyTester(
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaStart: Double,
    private val alphaEnd: Double,
    private val alphaSampleCount: Int,
    val precision: Int,
    private val simulationTicks: Int,
    val plotEnabled: Boolean,
    val assertionsEnabled: Boolean
) {
    init {
        check(alphaStart > 0.0 && alphaStart <= 1.0)
        check(alphaEnd > 0.0 && alphaEnd <= 1.0)
    }
    val alphas = if (alphaSampleCount == 1) {
        check(alphaStart == alphaEnd)
        listOf(alphaStart)
    } else {
        (0 until alphaSampleCount).map { alphaStart + it * ((alphaEnd - alphaStart) / (alphaSampleCount - 1)) }
    }

    fun run(numberOfThreads: Int = 1) {
        check(alphaSampleCount >= 1)
        check(baseSystemConfig.numberOfQueues == 1)
        require(numberOfThreads >= 1)
        val stochasticPolicies = alphas.map {
            val config = baseSystemConfig.withAlphaSingleQueue(it)
            if (numberOfThreads == 1) {
                RangedOptimalPolicyFinder.findOptimalPolicy(config, precision)
            } else {
                ConcurrentRangedOptimalPolicyFinder(config).findOptimalPolicy(precision, numberOfThreads)
            }
        }

        val simulationResults = simulateSingleThread(stochasticPolicies)

        if (plotEnabled) {
            val plot = Plot.create()

            plot.plot().add(alphas, simulationResults.localOnlyDelays).color("tab:olive").label("Local Only")
            plot.plot().add(alphas, simulationResults.offloadOnlyDelays).color("blue").label("Offload Only")
            plot.plot().add(alphas, simulationResults.greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
            plot.plot().add(alphas, simulationResults.greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
            plot.plot().add(alphas, simulationResults.stochasticDelays).color("red").label("Optimal Stochastic")

            plot.xlabel("The average arrival rate (alpha)")
            plot.ylabel("The average delay")
            plot.title("Average delay for policies")
            plot.ylim(0, 100)
            plot.xlim(alphaStart, alphaEnd)
            plot.legend()
            writeToFile(plot)
        }
    }

    private fun simulateSingleThread(stochasticPolicies: List<StochasticOffloadingPolicy>): DelayResults {
        val localOnlyDelays = mutableListOf<Double>()
        val offloadOnlyDelays = mutableListOf<Double>()
        val greedyOffloadFirstDelays = mutableListOf<Double>()
        val greedyLocalFirstDelays = mutableListOf<Double>()
        val stochasticDelays = mutableListOf<Double>()
        for ((i, alpha) in alphas.withIndex()) {
            val config = baseSystemConfig.withAlphaSingleQueue(alpha)
            val simulator = Simulator(config)
            println("Running simulations for alpha = $alpha")

            val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
            val offloadOnlyDelay = simulator.simulatePolicy(OffloadOnlyPolicy, simulationTicks).averageDelay
            val greedyOffloadFirstDelay =
                simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
            val greedyLocalFirstDelay =
                simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
            val stochasticDelay = simulator.simulatePolicy(stochasticPolicies[i], simulationTicks).averageDelay

            localOnlyDelays.add(localOnlyDelay)
            offloadOnlyDelays.add(offloadOnlyDelay)
            greedyOffloadFirstDelays.add(greedyOffloadFirstDelay)
            greedyLocalFirstDelays.add(greedyLocalFirstDelay)
            stochasticDelays.add(stochasticDelay)

            val errorWindowMultiplier = 0.99
            if (assertionsEnabled) {
                with(Truth.assertThat(stochasticDelay * errorWindowMultiplier)) {
                    isLessThan(localOnlyDelay)
                    isLessThan(offloadOnlyDelay)
                    isLessThan(greedyOffloadFirstDelay)
                    isLessThan(greedyLocalFirstDelay)
                }
            }
        }

        return DelayResults(
            localOnlyDelays, offloadOnlyDelays, greedyOffloadFirstDelays, greedyLocalFirstDelays, stochasticDelays
        )
    }

    internal data class DelayResults(
        val localOnlyDelays: List<Double>,
        val offloadOnlyDelays: List<Double>,
        val greedyOffloadFirstDelays: List<Double>,
        val greedyLocalFirstDelays: List<Double>,
        val stochasticDelays: List<Double>
    )
}