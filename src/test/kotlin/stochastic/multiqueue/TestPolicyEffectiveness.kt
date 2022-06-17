package stochastic.multiqueue

import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
import stochastic.lp.ConcurrentRangedOptimalPolicyFinder
import tester.PolicyEffectivenessTester

class TestPolicyEffectiveness {

    @Test
    fun testPolicyEffectivenessHeavyLight() {
        val config = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(9)

        val policyEffectivenessTester = PolicyEffectivenessTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.40, 3), AlphaRange.Variable(0.01, 0.40, 3)),
            precision = 10,
            simulationTicks = 1_000_000
        )

        val result = policyEffectivenessTester.runConcurrent(12)
        println(result)
        // Result(stochasticEffectivePercent=0.0, localOnlyEffectivePercent=77.77777777777779, offloadOnlyEffectivePercent=55.55555555555556, greedyOffloadFirstEffectivePercent=33.33333333333333, greedyLocalFirstEffectivePercent=33.33333333333333)
    }


    @Test
    fun testPolicyEffectivenessHeavyLight2() {
        val config = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(7)

        val policyEffectivenessTester = PolicyEffectivenessTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.50, 10), AlphaRange.Variable(0.01, 0.50, 10)),
            precision = 30,
            simulationTicks = 4_000_000
        )

        val result = policyEffectivenessTester.runConcurrent(32)
        println(result)
    }

    @Test
    fun testPolicyEffectivenessHeavyLight3() {
        val config = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(6).withAlpha(listOf(0.5, 0.5))
        val policy = ConcurrentRangedOptimalPolicyFinder(config).findOptimalPolicy(30, 32)
        println(policy)
    }

    @Test
    fun testPolicyEffectivenessHeavyLight4() {
        val config = Mock.doubleConfigHeavyLight().withTaskQueueCapacity(10)

        val policyEffectivenessTester = PolicyEffectivenessTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.50, 10), AlphaRange.Variable(0.01, 0.50, 10)),
            precision = 30,
            simulationTicks = 4_000_000
        )

        val result = policyEffectivenessTester.runConcurrent(12)
        println(result)
    }

    @Test
    fun testTripleQueue() {
        val config = Mock.tripleConfigHeavyLightMid().withTaskQueueCapacity(5)

        val policyEffectivenessTester = PolicyEffectivenessTester(
            baseSystemConfig = config,
            alphaRanges = listOf(AlphaRange.Variable(0.01, 0.20, 10), AlphaRange.Variable(0.01, 0.20, 10), AlphaRange.Variable(0.01, 0.20, 10)),
            precision = 10,
            simulationTicks = 1_000_000
        )

        val result = policyEffectivenessTester.run()
        println(result)
    }
}