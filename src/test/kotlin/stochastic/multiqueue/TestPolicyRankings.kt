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
            assertionsEnabled = true
        )

        val resultsSingleThread: PolicyRankingTester.DelayAverageRankingResult
        val millisSingle = measureTimeMillis {
            resultsSingleThread = tester.run()
        }
        val resultsConcurrent: PolicyRankingTester.DelayAverageRankingResult
        val millisConcurrent = measureTimeMillis {
            resultsConcurrent = tester.runConcurrent(4)
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
            baseSystemConfig = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(3),
            alphaRanges = listOf(AlphaRange.Constant(0.2), AlphaRange.Variable(0.01, 0.3, 10)),
            precision = 5,
            simulationTicks = 1_000_000,
            assertionsEnabled = true
        )

        val result = tester.runConcurrent(12)

        println(result)
        // DelayAverageRankingResult(localOnlyRankingPercents=[0.0, 0.0, 0.0, 0.0, 100.0], offloadOnlyRankingPercents=[0.0, 10.0, 30.0, 60.0, 0.0], greedyOffloadFirstRankingPercents=[0.0, 60.0, 40.0, 0.0, 0.0], greedyLocalFirstRankingPercents=[0.0, 30.0, 30.0, 40.0, 0.0], stochasticRankingPercents=[100.0, 0.0, 0.0, 0.0, 0.0])
    }
}