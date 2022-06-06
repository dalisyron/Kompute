package tester

import com.google.common.truth.Truth.assertThat
import core.*
import core.policy.*
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.ConcurrentRangedOptimalPolicyFinder
import stochastic.policy.StochasticOffloadingPolicy
import kotlin.concurrent.thread

class PolicyRankingTester(
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
    private val assertionsEnabled: Boolean,
    private val numberOfThreads: Int
) {

    data class DelayCountState(
        val localOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0),
        val offloadOnlyRankingCount: MutableList<Int> = mutableListOfInt(5, 0),
        val greedyOffloadFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0),
        val greedyLocalFirstRankingCount: MutableList<Int> = mutableListOfInt(5, 0),
        val stochasticRankingCount: MutableList<Int> = mutableListOfInt(5, 0),
    )

    private val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })
    private lateinit var stochasticPolicies: List<StochasticOffloadingPolicy>

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

    fun run(): DelayAverageRankingResult {
        val countState = DelayCountState()

        val threadCount = Integer.min(numberOfThreads, alphaCombinations.size)
        val alphaBatches = alphaCombinations.splitEqual(threadCount)
        val batchSizes = alphaBatches.map { it.size }.toCumulative()

        stochasticPolicies = alphaCombinations.map {
            ConcurrentRangedOptimalPolicyFinder(baseSystemConfig.withAlpha(it)).findOptimalPolicy(
                precision,
                numberOfThreads
            )
        }

        val threads = (0 until threadCount).map { i ->
            thread(start = false) {
                for (index in alphaBatches[i].indices) {
                    val alphaIndex = (if (i == 0) 0 else batchSizes[i - 1]) + index
                    val delayResult: DelayAverageRankingItem = getDelaysForAlpha(alphaIndex)

                    if (assertionsEnabled) {
                        validateAlphaDelayResult(delayResult)
                    }
                    updateRankingCounts(countState, delayResult)
                }

            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        return DelayAverageRankingResult(
            localOnlyRankingPercents = toPercent(countState.localOnlyRankingCount),
            offloadOnlyRankingPercents = toPercent(countState.offloadOnlyRankingCount),
            greedyOffloadFirstRankingPercents = toPercent(countState.greedyOffloadFirstRankingCount),
            greedyLocalFirstRankingPercents = toPercent(countState.greedyLocalFirstRankingCount),
            stochasticRankingPercents = toPercent(countState.stochasticRankingCount)
        )
    }

    private fun toPercent(rankCounts: List<Int>): List<Double> {
        return rankCounts.map { (it.toDouble() / alphaCombinations.size.toDouble()) * 100.0 }
    }

    @Synchronized
    fun updateRankingCounts(countState: DelayCountState, rankingItem: DelayAverageRankingItem) {
        with(countState) {
            localOnlyRankingCount[rankingItem.localOnlyRank]++
            offloadOnlyRankingCount[rankingItem.offloadOnlyRank]++
            greedyOffloadFirstRankingCount[rankingItem.greedyOffloadFirstDelay]++
            greedyLocalFirstRankingCount[rankingItem.greedyLocalFirstRank]++
            stochasticRankingCount[rankingItem.stochasticRank]++
        }
    }

    private fun validateAlphaDelayResult(delayItem: DelayAverageRankingItem) {
        assertThat(delayItem.stochasticRank)
            .isEqualTo(0)
    }

    private fun getDelaysForAlpha(alphaIndex: Int): DelayAverageRankingItem {
        val alpha = alphaCombinations[alphaIndex]
        val config = baseSystemConfig.withAlpha(alpha)
        val simulator = Simulator(config)
        println("Running simulations for alpha = $alpha")

        val localOnlyDelay = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks).averageDelay
        val offloadOnlyDelay = simulator.simulatePolicy(OffloadOnlyPolicy, simulationTicks).averageDelay
        val greedyOffloadFirstDelay =
            simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks).averageDelay
        val greedyLocalFirstDelay =
            simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks).averageDelay
        val stochasticDelay = simulator.simulatePolicy(stochasticPolicies[alphaIndex], simulationTicks).averageDelay

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
}
