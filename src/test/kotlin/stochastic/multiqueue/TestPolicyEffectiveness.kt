package stochastic.multiqueue

import core.policy.AlphaRange
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import org.junit.jupiter.api.Test
import simulation.app.Mock
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
}