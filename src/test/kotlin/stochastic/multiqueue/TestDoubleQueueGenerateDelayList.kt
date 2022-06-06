package stochastic.multiqueue

import core.policy.AlphaRange
import org.junit.jupiter.api.Test
import simulation.app.Mock
import tester.DelayListTester

class TestDoubleQueueGenerateDelayList {

    @Test
    fun testHeavyLight() {
        val config = Mock.doubleConfigHeavyLight()
        val tester = DelayListTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.45, 1), AlphaRange.Variable(0.01, 0.45, 1)),
            precision = 30,
            simulationTicks = 3_000_000
        )

        val result = tester.runConcurrent(32)
        println(result)
    }
}