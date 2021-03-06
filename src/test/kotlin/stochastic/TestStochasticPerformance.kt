package stochastic

import com.google.common.truth.Truth.assertThat
import core.environment.EnvironmentParameters
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.jupiter.api.Test
import org.junit.experimental.categories.Category
import core.ue.OffloadingSystemConfig.Companion.withAlphaSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withEtaConfig
import core.ue.OffloadingSystemConfig.Companion.withEtaConfigSingleQueue
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSectionsSingleQueue
import simulation.app.Mock
import stochastic.lp.EquationRow
import stochastic.lp.OffloadingLPCreator
import stochastic.lp.RangedOptimalPolicyFinder
import kotlin.system.measureTimeMillis

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
            val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyWithGivenEta(config)
            println("estimated average delay = ${optimalPolicy.averageDelay}")
        }
    }

    @Test
    @Category(PerformanceTests::class)
    fun testRanged1() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was 4 min 19 sec
        val baseConfig = getSimpleConfig().withAlphaSingleQueue(0.1).withBeta(0.9).withTaskQueueCapacity(50)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(baseConfig, 20)
        println(optimalPolicy.averageDelay)
    }

    @Test
    @Category(PerformanceTests::class)
    fun testBig() {
        // run time in e1c21057b88ede25d4953f21f8e71db73e34dd12 was +10 min
        val config = getSimpleConfig().withEtaConfigSingleQueue(0.9).withTaskQueueCapacity(80).withNumberOfSectionsSingleQueue(30)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyWithGivenEta(config)
        println(optimalPolicy.averageDelay)
    }

    @Test
    @Category(PerformanceTests::class)
    fun testSingle() {
        val config = getSimpleConfig().withAlphaSingleQueue(0.1).withBeta(0.9).withTaskQueueCapacity(50)
        val optimalPolicy = RangedOptimalPolicyFinder.findOptimalPolicyWithGivenEta(config)
        println(optimalPolicy.averageDelay)
    }

    @Test
    fun testCreateLargeLP() {
        val config = Mock.doubleConfigHeavyLight()
        val lp = OffloadingLPCreator(config.withEtaConfig(listOf(0.3, 0.1))).createOffloadingLinearProgram()
        // Runtime in L was 12528 ms
    }

    @Test
    fun testCompareV2() {
        val config = Mock.doubleQueueConfig1().withTaskQueueCapacity(7)
        val equationList4: List<EquationRow>
        val timeV1 = measureTimeMillis {
            equationList4 = OffloadingLPCreator(config).getEquations4()
        }
        val equationList4V2: List<EquationRow>
        val timeV2 = measureTimeMillis {
            equationList4V2 = OffloadingLPCreator(config).getEquations4V2()
        }

        equationList4.forEachIndexed { i, equation ->
            assertThat(equation.type)
                .isEqualTo(equationList4V2[i].type)

            equation.coefficients.forEachIndexed { index, d ->
                assertThat(d)
                    .isWithin(1e-4)
                    .of(equationList4V2[i].coefficients[index])
            }
        }
        println("$timeV1 ms | $timeV2 ms")
    }
}