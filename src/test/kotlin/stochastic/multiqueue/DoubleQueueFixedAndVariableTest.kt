package stochastic.multiqueue

import com.google.common.truth.Truth.assertThat
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.Test
import plot.PlotterFixedAndRanging
import simulation.app.Mock
import stochastic.policy.AlphaRange
import stochastic.policy.MultiQueueRangedAlphaTester
import kotlin.system.measureTimeMillis

class DoubleQueueFixedAndVariableTest {

    @Test
    fun testDoubleQueueFixedAndVariable1() {
        val doubleQueueConfig = Mock.doubleQueueConfig1()

        val tester = MultiQueueRangedAlphaTester(
            baseSystemConfig = doubleQueueConfig,
            alphaRanges = listOf(AlphaRange.Variable(0.20, 0.20, 1), AlphaRange.Constant(0.20)),
            precision = 10,
            simulationTicks = 4_000_000,
            assertionsEnabled = true
        )

        val result = tester.run()
        PlotterFixedAndRanging.plot(result)
    }

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

        val resultConcurrent: MultiQueueRangedAlphaTester.Result
        val runtimeConcurrent = measureTimeMillis {
            resultConcurrent = tester.runConcurrent(4)
        }

        println("Finished concurrent in $runtimeConcurrent ms")
        val resultSingleThreaded: MultiQueueRangedAlphaTester.Result
        val runtimeSingleThread = measureTimeMillis {
            resultSingleThreaded = tester.run()
        }

        resultConcurrent.stochasticDelays.forEachIndexed { index, value ->
            assertThat(value)
                .isWithin(1e-3)
                .of(resultSingleThreaded.stochasticDelays[index])
        }

        // Run result in 595aa9d Concurrent = 75888 ms | Single = 145262 ms
        println("Concurrent = $runtimeConcurrent ms | Single = $runtimeSingleThread ms")
    }
}