package stochastic

import com.google.common.truth.Truth
import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.Test
import org.junit.experimental.categories.Category
import policy.Action
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import stochastic.lp.RangedOptimalPolicyFinder

interface PerformanceTests

class StochasticPerformanceTests {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 15, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 10
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.1,
                beta = 0.9,
                eta = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLoc = 1.5,
                pMax = 500.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters,
            allActions = setOf(
                Action.NoOperation,
                Action.AddToCPU,
                Action.AddToTransmissionUnit,
                Action.AddToBothUnits
            )
        )

        return systemCofig
    }

    @Test
    @Category(PerformanceTests::class)
    fun testLoop1() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was 6 min 25 sec
        val etas = (1..20).map { it * 2.0 / 100.0 }
        etas.forEach {
            println("testing for eta = $it")
            val config = getSimpleConfig().withTaskQueueCapacity(50).withEta(it)
            val optimalPolicy = OptimalPolicyFinder.findOptimalPolicy(config)
            println("estimated average delay = ${optimalPolicy.averageDelay}")
        }
    }

    @Test
    @Category(PerformanceTests::class)
    fun testRanged1() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was 4 min 19 sec
        val baseConfig = getSimpleConfig().withAlpha(0.1).withBeta(0.9).withTaskQueueCapacity(50)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(baseConfig, 30)
        println(optimalPolicy.averageDelay)
    }

    @Test
    @Category(PerformanceTests::class)
    fun testBig() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was +10 min
        val config = getSimpleConfig().withEta(0.9).withTaskQueueCapacity(80).withNumberOfSections(30)
        val optimalPolicy = OptimalPolicyFinder.findOptimalPolicy(config)
        println(optimalPolicy.averageDelay)
    }
}