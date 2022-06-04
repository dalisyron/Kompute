package stochastic.multiqueue

import com.google.common.truth.Truth
import core.policy.AlphaDelayResults
import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.Test
import simulation.app.Mock
import stochastic.policy.MultiQueueRangedAlphaTester
import kotlin.system.measureTimeMillis

class TestDoubleQueueFixedAndVariable2 {

    @Test
    fun testDoubleQueueFixedAndVariableConcurrent() {
        val doubleQueueConfig = Mock.doubleQueueConfig1().withTaskQueueCapacity(5).withNumberOfSections(listOf(4, 2))

        val tester = MultiQueueRangedAlphaTester(
            baseSystemConfig = doubleQueueConfig,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.20, 4), AlphaRange.Constant(0.20)),
            precision = 3,
            simulationTicks = 4_000_000,
            assertionsEnabled = false
        )

        val alphaDelayResultsConcurrent: AlphaDelayResults
        val runtimeConcurrent = measureTimeMillis {
            alphaDelayResultsConcurrent = tester.runConcurrent(4)
        }

        println("Finished concurrent in $runtimeConcurrent ms")
        val alphaDelayResultsSingleThreaded: AlphaDelayResults
        val runtimeSingleThread = measureTimeMillis {
            alphaDelayResultsSingleThreaded = tester.run()
        }

        alphaDelayResultsConcurrent.stochasticDelays.forEachIndexed { index, value ->
            Truth.assertThat(value)
                .isWithin(1e-3)
                .of(alphaDelayResultsSingleThreaded.stochasticDelays[index])
        }

        // Run result in 595aa9d Concurrent = 75888 ms | Single = 145262 ms
        println("Concurrent = $runtimeConcurrent ms | Single = $runtimeSingleThread ms")
    }
}