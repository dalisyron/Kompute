package stochastic.multiqueue

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
import tester.PolicyEffectivenessTester
import tester.PolicyRankingTester
import kotlin.system.measureTimeMillis

class TestPolicyRankings {

    @Test
    fun compareSingleThreadWithConcurrent() {
        val tester = PolicyRankingTester(
            baseSystemConfig = Mock.doubleConfigHeavyLight().withNumberOfSections(listOf(3, 2)),
            alphaRanges = listOf(AlphaRange.Constant(0.2), AlphaRange.Variable(0.01, 0.3, 10)),
            precision = 4,
            simulationTicks = 1_000_000,
            assertionsEnabled = true,
            numberOfThreads = 24
        )

        val resultsSingleThread: PolicyRankingTester.DelayAverageRankingResult
        val millisSingle = measureTimeMillis {
            resultsSingleThread = tester.run()
        }
        val resultsConcurrent: PolicyRankingTester.DelayAverageRankingResult
        val millisConcurrent = measureTimeMillis {
            resultsConcurrent = tester.run()
        }

        resultsSingleThread.stochasticRankingPercents.forEachIndexed { idx, percent ->
            assertThat(percent)
                .isWithin(1e-3)
                .of(resultsConcurrent.stochasticRankingPercents[idx])

        }

        println(resultsSingleThread)
        println(resultsConcurrent)
        println("Single thread running time = $millisSingle | Multi thread running time = $millisConcurrent")
    }

    @Test
    fun testHeavyLight1() {
        val tester = PolicyRankingTester(
            baseSystemConfig = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(12),
            alphaRanges = listOf(AlphaRange.Constant(0.2), AlphaRange.Variable(0.01, 0.3, 10)),
            precision = 5,
            simulationTicks = 2_000_000,
            assertionsEnabled = true,
            numberOfThreads = 24
        )

        val result = tester.run()

        println(result)
        // DelayAverageRankingResult(localOnlyRankingPercents=[0.0, 0.0, 0.0, 0.0, 100.0], offloadOnlyRankingPercents=[0.0, 10.0, 30.0, 60.0, 0.0], greedyOffloadFirstRankingPercents=[0.0, 60.0, 40.0, 0.0, 0.0], greedyLocalFirstRankingPercents=[0.0, 30.0, 30.0, 40.0, 0.0], stochasticRankingPercents=[100.0, 0.0, 0.0, 0.0, 0.0])
        // DelayAverageRankingResult(localOnlyRankingPercents=[0.0, 0.0, 0.0, 0.0, 100.0], offloadOnlyRankingPercents=[0.0, 20.0, 20.0, 60.0, 0.0], greedyOffloadFirstRankingPercents=[0.0, 50.0, 50.0, 0.0, 0.0], greedyLocalFirstRankingPercents=[0.0, 30.0, 30.0, 40.0, 0.0], stochasticRankingPercents=[100.0, 0.0, 0.0, 0.0, 0.0])
    }

    @Test
    fun testTripleQueue() {
        val config = Mock.tripleConfigHeavyLightMid()
        val alphaRanges = listOf<AlphaRange>(
            AlphaRange.Variable(0.40, 0.40, 1),
            AlphaRange.Variable(0.42, 0.42, 1),
            AlphaRange.Variable(0.45, 0.45, 1)
        )

        val tester = PolicyRankingTester(
            baseSystemConfig = config,
            alphaRanges = alphaRanges,
            precision = 30,
            simulationTicks = 4_000_000,
            assertionsEnabled = false,
            numberOfThreads = 24
        )

        val results = tester.run()
        println(results)
    }
}