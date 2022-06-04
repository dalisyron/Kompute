package stochastic.multiqueue

import com.google.common.truth.Truth.assertThat
import core.plot.PlotterFixedAndRanging
import simulation.app.Mock
import core.policy.AlphaRange
import org.junit.jupiter.api.Test
import tester.MultiQueueRangedAlphaTester

class TestDoubleQueueFixedAndVariable {

    @Test
    fun testDoubleQueueFixedAndVariable1() {
        val doubleQueueConfig = Mock.doubleQueueConfig1()

        val tester = MultiQueueRangedAlphaTester(
            baseSystemConfig = doubleQueueConfig,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.20, 20), AlphaRange.Constant(0.20)),
            precision = 10,
            simulationTicks = 4_000_000,
            assertionsEnabled = true
        )

        val result = tester.runConcurrent(8)
        PlotterFixedAndRanging.plot(result)
    }

    @Test
    fun testCore() {
        val numberOfCores = Runtime.getRuntime().availableProcessors()
        println("running coreTest")
        assertThat(numberOfCores)
            .isGreaterThan(2)
    }
}