package tester

import com.google.common.truth.Truth
import core.cartesianProduct
import core.mutableListOfZeros
import core.policy.*
import core.splitEqual
import core.toCumulative
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.RangedOptimalPolicyFinder
import kotlin.concurrent.thread

class MultiQueueRangedAlphaTester (
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
    private val assertionsEnabled: Boolean
) {

    init {
        require(alphaRanges.isNotEmpty())
    }

    fun run(): AlphaDelayResults {
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })

        val localOnlyDelays: MutableList<Double> = mutableListOf()
        val offloadOnlyDelays: MutableList<Double> = mutableListOf()
        val greedyOffloadFirstDelays: MutableList<Double> = mutableListOf()
        val greedyLocalFirstDelays: MutableList<Double> = mutableListOf()
        val stochasticDelays: MutableList<Double> = mutableListOf()

        alphaCombinations.forEachIndexed { index, alpha: List<Double> ->
            val delayResult: AlphaDelayResult = getDelaysForAlpha(alpha)

            if (assertionsEnabled) {
                validateAlphaDelayResult(delayResult)
            }

            with(delayResult) {
                localOnlyDelays.add(localOnlyDelay)
                offloadOnlyDelays.add(localOnlyDelay)
                greedyOffloadFirstDelays.add(greedyOffloadFirstDelay)
                greedyLocalFirstDelays.add(greedyLocalFirstDelay)
                stochasticDelays.add(stochasticDelay)
            }
        }

        return AlphaDelayResults(
            alphaRanges = alphaRanges,
            localOnlyDelays = localOnlyDelays,
            offloadOnlyDelays = offloadOnlyDelays,
            greedyOffloadFirstDelays = greedyOffloadFirstDelays,
            greedyLocalFirstDelays = greedyLocalFirstDelays,
            stochasticDelays = stochasticDelays
        )
    }

    fun runConcurrent(numberOfThreads: Int): AlphaDelayResults {
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })
        val alphaCount = alphaCombinations.size

        val localOnlyDelays: MutableList<Double> = mutableListOfZeros(alphaCount)
        val offloadOnlyDelays: MutableList<Double> = mutableListOfZeros(alphaCount)
        val greedyOffloadFirstDelays: MutableList<Double> = mutableListOfZeros(alphaCount)
        val greedyLocalFirstDelays: MutableList<Double> = mutableListOfZeros(alphaCount)
        val stochasticDelays: MutableList<Double> = mutableListOfZeros(alphaCount)

        println(alphaCombinations.size)
        val alphaBatches = alphaCombinations.splitEqual(numberOfThreads)
        val batchSizeAccumulative = alphaBatches.map { it.size }.toCumulative()

        val threads: MutableList<Thread> = mutableListOf()
        for (i in 0 until numberOfThreads) {
            val temp = thread(start = false) {
                println("In thread $i | ${alphaBatches[i].size}")
                alphaBatches[i].forEachIndexed { index, alpha: List<Double> ->
                    // System.err.println(">>>>>> In Thread $it")
                    val delayResult: AlphaDelayResult = getDelaysForAlpha(alpha)
                    val delayIndex = (if (i > 0) batchSizeAccumulative[i - 1] else 0) + index

                    if (assertionsEnabled) {
                        validateAlphaDelayResult(delayResult)
                    }

                    require(localOnlyDelays[delayIndex] == 0.0)
                    require(offloadOnlyDelays[delayIndex] == 0.0)
                    require(greedyOffloadFirstDelays[delayIndex] == 0.0)
                    require(greedyLocalFirstDelays[delayIndex] == 0.0)
                    require(stochasticDelays[delayIndex] == 0.0)

                    localOnlyDelays[delayIndex] = delayResult.localOnlyDelay
                    offloadOnlyDelays[delayIndex] = delayResult.localOnlyDelay
                    greedyOffloadFirstDelays[delayIndex] = delayResult.greedyOffloadFirstDelay
                    greedyLocalFirstDelays[delayIndex] = delayResult.greedyLocalFirstDelay
                    stochasticDelays[delayIndex] = delayResult.stochasticDelay
                }
            }
            threads.add(temp)
        }

        threads.forEach {
            it.start()
        }

        threads.forEach {
            it.join()
        }
        return AlphaDelayResults(
            alphaRanges = alphaRanges,
            localOnlyDelays = localOnlyDelays,
            offloadOnlyDelays = offloadOnlyDelays,
            greedyOffloadFirstDelays = greedyOffloadFirstDelays,
            greedyLocalFirstDelays = greedyLocalFirstDelays,
            stochasticDelays = stochasticDelays
        )
    }

    private fun validateAlphaDelayResult(delayResult: AlphaDelayResult) {
        val errorWindowMultiplier = 0.99
        with(Truth.assertThat(delayResult.stochasticDelay * errorWindowMultiplier)) {
            isLessThan(delayResult.localOnlyDelay)
            isLessThan(delayResult.offloadOnlyDelay)
            isLessThan(delayResult.greedyOffloadFirstDelay)
            isLessThan(delayResult.greedyLocalFirstDelay)
        }
    }

    private fun getDelaysForAlpha(alpha: List<Double>): AlphaDelayResult {
        val config = baseSystemConfig.withAlpha(alpha)
        val simulator = Simulator(config)
        val stochastic = RangedOptimalPolicyFinder.findOptimalPolicy(config, precision)
        println("Running simulations for alpha = $alpha")

        val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
        val offloadOnlyDelay = simulator.simulatePolicy(OffloadOnlyPolicy, simulationTicks).averageDelay
        val greedyOffloadFirstDelay =
            simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
        val greedyLocalFirstDelay =
            simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
        val stochasticDelay = simulator.simulatePolicy(stochastic, simulationTicks).averageDelay

        return AlphaDelayResult(
            localOnlyDelay = localOnlyDelay,
            offloadOnlyDelay = offloadOnlyDelay,
            greedyOffloadFirstDelay = greedyOffloadFirstDelay,
            greedyLocalFirstDelay = greedyLocalFirstDelay,
            stochasticDelay = stochasticDelay
        )
    }

    internal data class AlphaDelayResult(
        val localOnlyDelay: Double,
        val offloadOnlyDelay: Double,
        val greedyOffloadFirstDelay: Double,
        val greedyLocalFirstDelay: Double,
        val stochasticDelay: Double
    )
}
