package stochastic.multiqueue

import com.google.common.truth.Truth.assertThat
import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
import tester.PolicyEffectivenessTester
import kotlin.system.measureTimeMillis

class TestDoubleQueueEffectivePercent {

    @Test
    fun testCompareConcurrentWithSingleThread() {
        val tester = PolicyEffectivenessTester(
            baseSystemConfig = Mock.doubleConfigHeavyLight().withNumberOfSections(listOf(3, 2)).withTaskQueueCapacity(5),
            alphaRanges = listOf(AlphaRange.Constant(0.2), AlphaRange.Variable(0.01, 0.3, 10)),
            precision = 4,
            simulationTicks = 1_000_000
        )

        val resultsSingleThread: PolicyEffectivenessTester.Result
        val millisSingle = measureTimeMillis {
            resultsSingleThread = tester.run()
        }
        val resultsConcurrent: PolicyEffectivenessTester.Result
        val millisConcurrent = measureTimeMillis {
            resultsConcurrent = tester.runConcurrent(4)
        }

        assertThat(resultsSingleThread.localOnlyEffectivePercent)
            .isWithin(1e-3)
            .of(resultsConcurrent.localOnlyEffectivePercent)

        assertThat(resultsSingleThread.offloadOnlyEffectivePercent)
            .isWithin(1e-3)
            .of(resultsConcurrent.offloadOnlyEffectivePercent)

        assertThat(resultsSingleThread.greedyLocalFirstEffectivePercent)
            .isWithin(1e-3)
            .of(resultsConcurrent.greedyLocalFirstEffectivePercent)

        assertThat(resultsSingleThread.greedyOffloadFirstEffectivePercent)
            .isWithin(1e-3)
            .of(resultsConcurrent.greedyOffloadFirstEffectivePercent)

        println("Single thread running time = $millisSingle | Multi thread running time = $millisConcurrent")

        // Run in 5d8cec1: Single thread running time = 218994 | Multi thread running time = 100750
    }
}