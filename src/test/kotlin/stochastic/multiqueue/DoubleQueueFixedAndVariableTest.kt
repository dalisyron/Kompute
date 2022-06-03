package stochastic.multiqueue

import org.junit.Test
import plot.PlotterFixedAndRanging
import simulation.app.Mock
import stochastic.policy.AlphaRange
import stochastic.policy.MultiQueueRangedAlphaTester

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
}