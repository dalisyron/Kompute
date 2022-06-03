package stochastic

import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.Test
import org.junit.experimental.categories.Category
import core.ue.OffloadingSystemConfig.Companion.withAlphaSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withEtaConfigSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSectionsSingleQueue
import stochastic.lp.RangedOptimalPolicyFinder

interface PerformanceTests

class StochasticPerformanceTests {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters.singleQueue(
            nCloud = 1,
            tRx = 0.0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig.singleQueue(
                taskQueueCapacity = 15, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 10
            ),
            componentsConfig = UserEquipmentComponentsConfig.singleQueue(
                alpha = 0.1,
                beta = 0.9,
                etaConfig = 0.0, // Not used in the baseline policies, set to whatever
                pTx = 1.5,
                pLocal = 1.5,
                pMax = 500.0
            )
        )
        val systemCofig = OffloadingSystemConfig(
            userEquipmentConfig = userEquipmentConfig,
            environmentParameters = environmentParameters
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
            val config = getSimpleConfig().withTaskQueueCapacity(50).withEtaConfigSingleQueue(it)
            val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config)
            println("estimated average delay = ${optimalPolicy.averageDelay}")
        }
    }

    @Test
    @Category(PerformanceTests::class)
    fun testRanged1() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was 4 min 19 sec
        val baseConfig = getSimpleConfig().withAlphaSingleQueue(0.1).withBeta(0.9).withTaskQueueCapacity(50)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(baseConfig, 20)
        println(optimalPolicy.averageDelay)
    }

    @Test
    @Category(PerformanceTests::class)
    fun testBig() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was +10 min
        val config = getSimpleConfig().withEtaConfigSingleQueue(0.9).withTaskQueueCapacity(80).withNumberOfSectionsSingleQueue(30)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config)
        println(optimalPolicy.averageDelay)
    }

    @Test
    @Category(PerformanceTests::class)
    fun testSingle() {
        val config = getSimpleConfig().withAlphaSingleQueue(0.1).withBeta(0.9).withTaskQueueCapacity(50)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyForGivenEta(config)
        println(optimalPolicy.averageDelay)
    }
}