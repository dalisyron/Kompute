package stochastic.multiqueue

import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
import tester.DelayListTester

class TestDoubleQueueGenerateDelayList {

    @Test
    fun testHeavyLight() {
        val config = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(8)
        val tester = DelayListTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.48, 10), AlphaRange.Variable(0.01, 0.48, 10)),
            precision = 30,
            simulationTicks = 4_000_000
        )

        val result = tester.runConcurrent(24)
        println(result)
    }
}