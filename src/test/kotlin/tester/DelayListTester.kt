package tester

import core.*
import core.policy.*
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.ConcurrentRangedOptimalPolicyFinder
import java.lang.Integer.min
import kotlin.concurrent.thread

class DelayListTester(
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
) {

    val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })
    val delayPerAlphaIndex: MutableList<Double> = mutableListOfZeros(alphaCombinations.size)

    init {
        require(alphaRanges.isNotEmpty())
    }

    fun runConcurrent(numberOfThreads: Int): Result {

        val threadCount = min(numberOfThreads, alphaCombinations.size)
        val alphaBatches = alphaCombinations.splitEqual(threadCount)
        val batchSizeAccumulative = alphaBatches.map { it.size }.toCumulative()

        val stochasticPolicies = alphaCombinations.map {
            ConcurrentRangedOptimalPolicyFinder(baseSystemConfig.withAlpha(it)).findOptimalPolicy(precision, numberOfThreads)
        }

        val threads = (0 until threadCount).map { i ->
            thread(start = false) {
                for ((index: Int, alpha: List<Double>) in alphaBatches[i].withIndex()) {
                    val alphaConfig = baseSystemConfig.withAlpha(alpha)
                    val alphaIndex = (if (i > 0) batchSizeAccumulative[i - 1] else 0) + index

                    val simulator = Simulator(alphaConfig)
                    val delay = simulator.simulatePolicy(stochasticPolicies[alphaIndex], simulationTicks).averageDelay
                    putDelay(alphaIndex, delay)
                }

            }
        }

        threads.forEach {
            it.start()
        }

        threads.forEach {
            it.join()
        }

        return Result(
            alphaCombinations,
            delayPerAlphaIndex
        )
    }

    @Synchronized
    fun putDelay(ind: Int, delay: Double) {
        delayPerAlphaIndex[ind] = delay
    }

    data class Result(
        val alphaCombinations: List<List<Double>>,
        val delays: List<Double>
    )
}