package tester

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import core.*
import core.policy.*
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.RangedOptimalPolicyFinder
import kotlin.concurrent.thread

class PolicyRankingTester(
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
    private val assertionsEnabled: Boolean
) {
    private var localOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
    private var offloadOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
    private var greedyOffloadFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
    private var greedyLocalFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
    private var stochasticRankingCount: MutableList<Int> = mutableListOfInt(5, 0)

    init {
        require(alphaRanges.isNotEmpty())
    }

    data class DelayAverageRankingItem(
        val localOnlyRank: Int,
        val offloadOnlyRank: Int,
        val greedyLocalFirstRank: Int,
        val greedyOffloadFirstDelay: Int,
        val stochasticRank: Int,
    )

    var alphaCount: Int? = null

    fun run(): DelayAverageRankingResult {
        reset()
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })
        alphaCount = alphaCombinations.size

        val localOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
        val offloadOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
        val greedyOffloadFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
        val greedyLocalFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0)
        val stochasticRankingCount: MutableList<Int> = mutableListOfInt(5, 0)

        alphaCombinations.forEachIndexed { index, alpha: List<Double> ->
            val delayResult: DelayAverageRankingItem = getDelaysForAlpha(alpha)

            localOnlyRankingCount[delayResult.localOnlyRank]++
            offloadOnlyRankingCount[delayResult.offloadOnlyRank]++
            greedyOffloadFirstRankingCount[delayResult.greedyOffloadFirstDelay]++
            greedyLocalFirstRankingCount[delayResult.greedyLocalFirstRank]++
            stochasticRankingCount[delayResult.stochasticRank]++

            if (assertionsEnabled) {
                validateAlphaDelayResult(delayResult)
            }
        }

        return DelayAverageRankingResult(
            localOnlyRankingPercents = toPercent(localOnlyRankingCount),
            offloadOnlyRankingPercents = toPercent(offloadOnlyRankingCount),
            greedyOffloadFirstRankingPercents = toPercent(greedyOffloadFirstRankingCount),
            greedyLocalFirstRankingPercents = toPercent(greedyLocalFirstRankingCount),
            stochasticRankingPercents = toPercent(stochasticRankingCount)
        )
    }

    private fun toPercent(rankCounts: List<Int>): List<Double> {
        return rankCounts.map { (it.toDouble() / alphaCount!!) * 100.0 }
    }

    fun runConcurrent(numberOfThreads: Int): DelayAverageRankingResult {
        reset()
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })
        alphaCount = alphaCombinations.size

        val threadCount = Integer.min(Integer.min(numberOfThreads, 8), alphaCombinations.size)
        val alphaBatches = alphaCombinations.splitEqual(threadCount)

        val threads = (0 until threadCount).map {
            thread(start = false) {
                for (alpha in alphaBatches[it]) {
                    val rankingItem: DelayAverageRankingItem = getDelaysForAlpha(alpha)

                    updateRankingCounts(rankingItem)
                    if (assertionsEnabled) {
                        validateAlphaDelayResult(rankingItem)
                    }
                }
            }
        }

        threads.forEach {
            it.start()
        }

        threads.forEach {
            it.join()
        }

        return DelayAverageRankingResult(
            localOnlyRankingPercents = toPercent(localOnlyRankingCount),
            offloadOnlyRankingPercents = toPercent(offloadOnlyRankingCount),
            greedyOffloadFirstRankingPercents = toPercent(greedyOffloadFirstRankingCount),
            greedyLocalFirstRankingPercents = toPercent(greedyLocalFirstRankingCount),
            stochasticRankingPercents = toPercent(stochasticRankingCount)
        )
    }

    @Synchronized
    fun updateRankingCounts(rankingItem: DelayAverageRankingItem) {
        localOnlyRankingCount[rankingItem.localOnlyRank]++
        offloadOnlyRankingCount[rankingItem.offloadOnlyRank]++
        greedyOffloadFirstRankingCount[rankingItem.greedyOffloadFirstDelay]++
        greedyLocalFirstRankingCount[rankingItem.greedyLocalFirstRank]++
        stochasticRankingCount[rankingItem.stochasticRank]++
    }

    private fun validateAlphaDelayResult(delayItem: DelayAverageRankingItem) {
        assertThat(delayItem.stochasticRank)
            .isEqualTo(0)
    }

    private fun getDelaysForAlpha(alpha: List<Double>): DelayAverageRankingItem {
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

        val items =
            listOf(localOnlyDelay, offloadOnlyDelay, greedyLocalFirstDelay, greedyOffloadFirstDelay, stochasticDelay)
        val itemsSorted = items.sorted()
        val itemRankings = items.map { itemsSorted.indexOf(it) }

        return DelayAverageRankingItem(
            itemRankings[0],
            itemRankings[1],
            itemRankings[2],
            itemRankings[3],
            itemRankings[4]
        )
    }

    data class DelayAverageRankingResult(
        val localOnlyRankingPercents: List<Double>,
        val offloadOnlyRankingPercents: List<Double>,
        val greedyOffloadFirstRankingPercents: List<Double>,
        val greedyLocalFirstRankingPercents: List<Double>,
        val stochasticRankingPercents: List<Double>
    )

    fun reset() {
        localOnlyRankingCount = mutableListOfInt(5, 0)
        offloadOnlyRankingCount = mutableListOfInt(5, 0)
        greedyOffloadFirstRankingCount = mutableListOfInt(5, 0)
        greedyLocalFirstRankingCount = mutableListOfInt(5, 0)
        stochasticRankingCount = mutableListOfInt(5, 0)
    }
}
