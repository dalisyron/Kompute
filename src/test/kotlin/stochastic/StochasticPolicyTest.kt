package stochastic

import com.google.common.truth.Truth.assertThat
import core.policy.GreedyOffloadFirstPolicy
import core.environment.EnvironmentParameters
import org.junit.Ignore
import org.junit.Test
import policy.Action
import simulation.simulation.Simulator
import stochastic.lp.OptimalPolicyFinder
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import core.ue.OffloadingSystemConfig.Companion.withBeta
import core.ue.OffloadingSystemConfig.Companion.withEta
import core.ue.OffloadingSystemConfig.Companion.withNumberOfSections
import core.ue.OffloadingSystemConfig.Companion.withPMax
import core.ue.OffloadingSystemConfig.Companion.withTaskQueueCapacity
import core.ue.UserEquipmentComponentsConfig
import core.ue.UserEquipmentConfig
import core.ue.UserEquipmentStateConfig
import org.junit.experimental.categories.Category
import stochastic.lp.RangedOptimalPolicyFinder

interface SlowTests

class StochasticPolicyTest {

    fun getSimpleConfig(): OffloadingSystemConfig {
        val environmentParameters = EnvironmentParameters(
            nCloud = 1,
            tRx = 0,
        )
        val userEquipmentConfig = UserEquipmentConfig(
            stateConfig = UserEquipmentStateConfig(
                taskQueueCapacity = 15, // set to some big number,
                tuNumberOfPackets = 1,
                cpuNumberOfSections = 17
            ),
            componentsConfig = UserEquipmentComponentsConfig(
                alpha = 0.2,
                beta = 0.4,
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
    @Category(SlowTests::class)
    fun testPolicyAverageBetterThanGreedyOffloadFirst() {
        val simpleConfig = getSimpleConfig().withTaskQueueCapacity(10).withEta(1e-9)
        val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(
            simpleConfig,
            50
        ) // 25 was 7.53 // 100 was 7.92 // 25 was 7.50 // 100 was 7.95

        val greedyOffloadFirstPolicy = GreedyOffloadFirstPolicy

        val simulator = Simulator(simpleConfig)
        val averageDelayStochastic = simulator.simulatePolicy(stochasticPolicy, 2000_000).averageDelay
        val averageDelayGof = simulator.simulatePolicy(greedyOffloadFirstPolicy, 2000_000).averageDelay

        println("Average delay for stochastic = $averageDelayStochastic with eta=${stochasticPolicy.stochasticPolicyConfig.eta} | Average delay for greedy = $averageDelayGof")

        assertThat(averageDelayStochastic)
            .isLessThan(averageDelayGof)
    }

    @Test
    @Category(SlowTests::class)
    fun testCompareSimulationWithLPForEta() {
        val etas = (1..40).map { it * 2.0 / 100.0 }

        etas.forEach {
            println("testing for eta = $it")
            val config = getSimpleConfig().withTaskQueueCapacity(100).withEta(it)
            val optimalPolicy = OptimalPolicyFinder.findOptimalPolicy(config)
            println("fullQueueProbability = ${optimalPolicy.fullQueueProbability()}")

            val averageDelayEstimate = optimalPolicy.averageDelay
            val simulator = Simulator(config)

            val averageDelaySimulation = simulator.simulatePolicy(optimalPolicy, 2_000_000).averageDelay

            println("estimate = $averageDelayEstimate | simulation = $averageDelaySimulation")

            assertThat(averageDelayEstimate)
                .isWithin(0.2)
                .of(averageDelaySimulation)
        }
    }

    @Test
    @Category(SlowTests::class)
    fun testCompareSimulationWithEtaAnomaly() {
        // Found in previous test runs
        assertSimulationEqualsEstimate(
            getSimpleConfig()
                .withEta(0.6)
                .withAlpha(0.2)
                .withBeta(0.6)
                .withNumberOfSections(3)
                .withTaskQueueCapacity(100)
                .withPMax(200.0)
        )
    }

    private fun assertSimulationEqualsEstimate(config: OffloadingSystemConfig) {
        val stochasticPolicy = OptimalPolicyFinder.findOptimalPolicy(config)
        val averageDelayEstimate = stochasticPolicy.averageDelay
        val simulator = Simulator(config)

        val averageDelaySimulation = simulator.simulatePolicy(stochasticPolicy, 20_000_000).averageDelay

        println("estimate = $averageDelayEstimate | simulation = $averageDelaySimulation")

        assertThat(averageDelayEstimate)
            .isWithin(1e-2)
            .of(averageDelaySimulation)
    }

}