package stochastic.multiqueue

import com.google.common.truth.Truth.assertThat
import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
import tester.PolicyRankingTester
import kotlin.system.measureTimeMillis

class TestDoubleQueueDelayRanking {

    @Test
    fun testCompareConcurrentWithSingleThread() {
        val tester = PolicyRankingTester(
            baseSystemConfig = Mock.doubleConfigHeavyLight().withNumberOfSections(listOf(3, 2)).withTaskQueueCapacity(5),
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

        resultsSingleThread.stochasticRankingPercents.forEachIndexed { index, d ->
            assertThat(d).isWithin(1e-3).of(resultsConcurrent.stochasticRankingPercents[index])
        }

        resultsSingleThread.localOnlyRankingPercents.forEachIndexed { index, d ->
            assertThat(d).isWithin(1e-3).of(resultsConcurrent.localOnlyRankingPercents[index])
        }

        resultsSingleThread.offloadOnlyRankingPercents.forEachIndexed { index, d ->
            assertThat(d).isWithin(1e-3).of(resultsConcurrent.offloadOnlyRankingPercents[index])
        }

        resultsSingleThread.greedyLocalFirstRankingPercents.forEachIndexed { index, d ->
            assertThat(d).isWithin(1e-3).of(resultsConcurrent.greedyLocalFirstRankingPercents[index])
        }

        resultsSingleThread.greedyOffloadFirstRankingPercents.forEachIndexed { index, d ->
            assertThat(d).isWithin(1e-3).of(resultsConcurrent.greedyOffloadFirstRankingPercents[index])
        }

        println("Single thread running time = $millisSingle | Multi thread running time = $millisConcurrent")

        // Run in 5d8cec1: Single thread running time = 218994 | Multi thread running time = 100750
    }

    @Test
    fun testHeavyLight1() {
        val tester = PolicyRankingTester(
            baseSystemConfig = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(7),
            alphaRanges = listOf(AlphaRange.Constant(0.2), AlphaRange.Variable(0.01, 0.3, 20)),
            precision = 10,
            simulationTicks = 2_000_000,
            assertionsEnabled = true,
            numberOfThreads = 24
        )

        val result = tester.run()

        println("===============\nResult is:$result\n===============")
    }
}