package tester

import com.github.sh0nk.matplotlib4j.Plot
import com.google.common.truth.Truth
import core.policy.GreedyLocalFirstPolicy
import core.policy.GreedyOffloadFirstPolicy
import core.policy.LocalOnlyPolicy
import core.policy.TransmitOnlyPolicy
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlphaSingleQueue
import simulation.simulation.Simulator
import stochastic.lp.RangedOptimalPolicyFinder

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

    fun run() {
        check(alphaSampleCount >= 1)
        check(baseSystemConfig.numberOfQueues == 1)

        val alphas = if (alphaSampleCount == 1) {
            check(alphaStart == alphaEnd)
            listOf(alphaStart)
        } else {
            (0 until alphaSampleCount).map { alphaStart + it * ((alphaEnd - alphaStart) / (alphaSampleCount - 1)) }
        }

        val localOnlyDelays = mutableListOf<Double>()
        val offloadOnlyDelays = mutableListOf<Double>()
        val greedyOffloadFirstDelays = mutableListOf<Double>()
        val greedyLocalFirstDelays = mutableListOf<Double>()
        val stochasticDelays = mutableListOf<Double>()

        for (alpha in alphas) {
            val config = baseSystemConfig.withAlphaSingleQueue(alpha)
            val simulator = Simulator(config)
            val stochastic = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config, precision)
            println("Running simulations for alpha = $alpha")

            val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
            val offloadOnlyDelay = simulator.simulatePolicy(TransmitOnlyPolicy, simulationTicks).averageDelay
            val greedyOffloadFirstDelay =
                simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
            val greedyLocalFirstDelay =
                simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
            val stochasticDelay = simulator.simulatePolicy(stochastic, simulationTicks).averageDelay

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

        if (plotEnabled) {
            val plot = Plot.create()

            plot.plot().add(alphas, localOnlyDelays).color("tab:olive").label("Local Only")
            plot.plot().add(alphas, offloadOnlyDelays).color("blue").label("Offload Only")
            plot.plot().add(alphas, greedyOffloadFirstDelays).color("green").label("Greedy (Offload First)")
            plot.plot().add(alphas, greedyLocalFirstDelays).color("cyan").label("Greedy (Local First)")
            plot.plot().add(alphas, stochasticDelays).color("red").label("Optimal Stochastic")

            plot.xlabel("The average arrival rate (alpha)")
            plot.ylabel("The average delay")
            plot.title("Average delay for policies")
            plot.ylim(0, 100)
            plot.xlim(alphaStart, alphaEnd)
            plot.legend()
            plot.show()
        }
    }
}